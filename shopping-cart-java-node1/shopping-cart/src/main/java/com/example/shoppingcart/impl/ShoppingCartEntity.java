package com.example.shoppingcart.impl;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.BackoffSupervisorStrategy;
import akka.cluster.sharding.typed.HashCodeMessageExtractor;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.ShardingMessageExtractor;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.cluster.sharding.typed.javadsl.EventSourcedEntityWithEnforcedReplies;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.CommandHandlerWithReplyBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.example.shoppingcart.impl.ShoppingCartCommand.Checkout;
import com.example.shoppingcart.impl.ShoppingCartCommand.Confirmed;
import com.example.shoppingcart.impl.ShoppingCartCommand.Get;
import com.example.shoppingcart.impl.ShoppingCartCommand.Rejected;
import com.example.shoppingcart.impl.ShoppingCartCommand.UpdateItem;
import com.example.shoppingcart.impl.ShoppingCartEvent.CheckedOut;
import com.example.shoppingcart.impl.ShoppingCartEvent.ItemUpdated;
import com.lightbend.lagom.javadsl.persistence.CommandEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * This is an event sourced entity. It has a state, {@link ShoppingCartState}, which
 * stores the current shopping cart items and whether it's checked out.
 *
 * Event sourced entities are interacted with by sending them commands. This
 * entity supports three commands, an {@link UpdateItem} command, which is used to
 * update the quantity of an item in the cart, a {@link Checkout} command which is
 * used to set checkout the shopping cart, and a {@link Get} command, which is a read
 * only command which returns the current shopping cart state.
 *
 * Commands get translated to events, and it's the events that get persisted by
 * the entity. Each event will have an event handler registered for it, and an
 * event handler simply applies an event to the current state. This will be done
 * when the event is first created, and it will also be done when the entity is
 * loaded from the database - each event will be replayed to recreate the state
 * of the entity.
 *
 * This entity defines two events, the {@link ItemUpdated} event, which is emitted
 * when a {@link UpdateItem} command is received, and a {@link CheckedOut} event, which
 * is emitted when a {@link Checkout} command is received.
 */
public class ShoppingCartEntity
  extends EventSourcedEntityWithEnforcedReplies<ShoppingCartCommand, ShoppingCartEvent, ShoppingCartState> {

  public static EntityTypeKey<ShoppingCartCommand> ENTITY_TYPE_KEY =
    EntityTypeKey.create(ShoppingCartCommand.class, "ShoppingCartEntity");

  private Logger logger = LoggerFactory.getLogger(this.getClass());

  public static void init(ActorSystem<?> system) {

    // This makes it possible to receive commands from Lagom's `PersistentEntityRef.ask`.
    // It can be removed after full rolling update.
    ShardingMessageExtractor<Object, ShoppingCartCommand> messageExtractor =
      new ShardingMessageExtractor<Object, ShoppingCartCommand>() {

        // Note that `HashCodeMessageExtractor` is using
        // `(math.abs(id.hashCode) % numberOfShards).toString`.
        // `akka.cluster.sharding.typed.HashCodeMessageExtractor` is compatible
        // Lagom's hashing function
        // and with `akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor`
        final HashCodeMessageExtractor delegate = new HashCodeMessageExtractor<ShoppingCartCommand>(
          system.settings().config().getInt("akka.cluster.sharding.number-of-shards")
        );

        @SuppressWarnings("unchecked")
        @Override
        public String entityId(Object message) {
          if (message instanceof CommandEnvelope) {
            return ((CommandEnvelope) message).entityId();
          } else if (message instanceof ShardingEnvelope) {
            return delegate.entityId((ShardingEnvelope) message);
          } else {
            return null;
          }
        }

        @Override
        public String shardId(String entityId) {
          return delegate.shardId(entityId);
        }

        @SuppressWarnings("unchecked")
        @Override
        public ShoppingCartCommand unwrapMessage(Object message) {
          if (message instanceof CommandEnvelope) {
            CommandEnvelope env = (CommandEnvelope) message;
            if (env.payload() instanceof ShoppingCartCommand)
              return (ShoppingCartCommand) env.payload();
            else
              return null;
          } else if (message instanceof ShardingEnvelope) {
            ShardingEnvelope<ShoppingCartCommand> env = (ShardingEnvelope<ShoppingCartCommand>) message;
            return (ShoppingCartCommand) delegate.unwrapMessage(env);
          } else {
            return null;
          }
        }
      };

    ClusterSharding.get(system).init(Entity.ofEventSourcedEntityWithEnforcedReplies(
      ShoppingCartEntity.ENTITY_TYPE_KEY,
      context -> ShoppingCartEntity.create(context.getEntityId()))
      .withMessageExtractor(messageExtractor));
  }

  public static ShoppingCartEntity create(String entityId) {
    return new ShoppingCartEntity(entityId, BackoffSupervisorStrategy.restartWithBackoff(
      Duration.ofSeconds(1), Duration.ofSeconds(10), 0.1));
  }

  private ShoppingCartEntity(String entityId,
                             BackoffSupervisorStrategy onPersistFailure) {
    super(ENTITY_TYPE_KEY, entityId, onPersistFailure);
  }

  @Override
  public ShoppingCartState emptyState() {
    return ShoppingCartState.EMPTY;
  }

  private final CheckedOutCommandHandlers checkedOutCommandHandlers = new CheckedOutCommandHandlers();
  private final OpenShoppingCartCommandHandlers openShoppingCartCommandHandlers = new OpenShoppingCartCommandHandlers();

  @Override
  public CommandHandlerWithReply<ShoppingCartCommand, ShoppingCartEvent, ShoppingCartState> commandHandler() {
    CommandHandlerWithReplyBuilder<ShoppingCartCommand, ShoppingCartEvent, ShoppingCartState> b =
      newCommandHandlerWithReplyBuilder();

    b.forState(state -> !state.isCheckedOut())
      .onCommand(UpdateItem.class, openShoppingCartCommandHandlers::onUpdateItem)
      .onCommand(Checkout.class, openShoppingCartCommandHandlers::onCheckout);

    b.forState(state -> state.isCheckedOut())
      .onCommand(UpdateItem.class, checkedOutCommandHandlers::onUpdateItem)
      .onCommand(Checkout.class, checkedOutCommandHandlers::onCheckout);

    b.forAnyState()
      .onCommand(Get.class, this::onGet);

    return b.build();
  }

  private ReplyEffect<ShoppingCartEvent, ShoppingCartState> onGet(ShoppingCartState state, Get cmd) {
    logger.info("getting entity cart state [" + entityId() + "]");
    return Effect().reply(cmd, state);
  }

  private class OpenShoppingCartCommandHandlers {

    public ReplyEffect<ShoppingCartEvent, ShoppingCartState> onUpdateItem(ShoppingCartState state, UpdateItem cmd) {
      if (cmd.getQuantity() < 0) {
        return Effect().reply(cmd, new Rejected("Quantity must be greater than zero"));
      } else if (cmd.getQuantity() == 0 && !state.getItems().containsKey(cmd.getProductId())) {
        return Effect().reply(cmd, new Rejected("Cannot delete item that is not already in cart"));
      } else {
        logger.info("updating entity cart [" + entityId() + "]");
        return Effect().persist(new ItemUpdated(entityId(), cmd.getProductId(), cmd.getQuantity(), Instant.now()))
          .thenReply(cmd, newState -> Confirmed.INSTANCE);
      }
    }

    public ReplyEffect<ShoppingCartEvent, ShoppingCartState> onCheckout(ShoppingCartState state, Checkout cmd) {
      if (state.getItems().isEmpty()) {
        return Effect().reply(cmd, new Rejected("Cannot checkout empty cart"));
      } else {
        return Effect().persist(new CheckedOut(entityId(), Instant.now()))
          .thenReply(cmd, newState -> Confirmed.INSTANCE);
      }
    }
  }

  private class CheckedOutCommandHandlers {
    ReplyEffect<ShoppingCartEvent, ShoppingCartState> onUpdateItem(UpdateItem cmd) {
      return Effect().reply(cmd, new Rejected("Can't update item on already checked out shopping cart"));
    }

    ReplyEffect<ShoppingCartEvent, ShoppingCartState> onCheckout(Checkout cmd) {
      return Effect().reply(cmd, new Rejected("Can't checkout on already checked out shopping cart"));
    }
  }


  @Override
  public EventHandler<ShoppingCartState, ShoppingCartEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
      .onEvent(ItemUpdated.class, this::onItemUpdated)
      .onEvent(CheckedOut.class, this::onCheckedOut)
      .build();
  }

  private ShoppingCartState onItemUpdated(ShoppingCartState state, ItemUpdated event) {
    return state.updateItem(event.getProductId(), event.getQuantity());
  }

  private ShoppingCartState onCheckedOut(ShoppingCartState state, CheckedOut event) {
    return state.checkout();
  }
}


