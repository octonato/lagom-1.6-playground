package com.example.shoppingcart.impl;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.example.shoppingcart.api.ShoppingCart;
import com.example.shoppingcart.api.ShoppingCartReportView;
import com.example.shoppingcart.api.ShoppingCartService;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.example.shoppingcart.impl.ShoppingCartCommand.OperationResult;

import javax.inject.Inject;

import com.example.shoppingcart.api.ShoppingCartItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;


/**
 * Implementation of the {@link ShoppingCartService}.
 */
public class ShoppingCartServiceImpl implements ShoppingCartService {

    private final ActorSystem<Void> system;
    private final ReportRepository reportRepository;
    private final ClusterSharding clusterSharding;

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Duration askTimeout = Duration.ofSeconds(3);

    // FIXME inject of akka.actor.typed.ActorSystem
    @Inject
    public ShoppingCartServiceImpl(akka.actor.ActorSystem untypedSystem, ReportRepository reportRepository) {
        this.system = Adapter.toTyped(untypedSystem);
        this.reportRepository = reportRepository;
        ShoppingCartEntity.init(system);
        clusterSharding = ClusterSharding.get(system);
    }

    private EntityRef<ShoppingCartCommand> entityRef(String id) {
        return clusterSharding.entityRefFor(ShoppingCartEntity.ENTITY_TYPE_KEY, id);
    }

    @Override
    public ServiceCall<NotUsed, ShoppingCart> get(String id) {
        logger.info("reading cart [" + id + "]");
        return request ->
          entityRef(id)
            .ask(ShoppingCartCommand.Get::new, askTimeout)
            .thenApply(cart -> convertShoppingCart(id, cart));
    }

    @Override
    public ServiceCall<NotUsed, ShoppingCartReportView> getReport(String id) {
        return request ->
                reportRepository.findById(id).thenApply(report -> {
                    if (report != null)
                        return new ShoppingCartReportView(id, report.getCreationDate(), report.getCheckoutDate());
                    else
                        throw new NotFound("Couldn't find a shopping cart report for '" + id + "'");
                });
    }

    @Override
    public ServiceCall<ShoppingCartItem, Done> updateItem(String id) {
        logger.info("updating cart [" + id + "]");
        return item ->
          convertOperationResult(
            entityRef(id)
              .ask((ActorRef<OperationResult> replyTo) ->
                new ShoppingCartCommand.UpdateItem(item.getProductId(), item.getQuantity(), replyTo), askTimeout)
          );
    }

    @Override
    public ServiceCall<NotUsed, Done> checkout(String id) {
        return request ->
          convertOperationResult(
            entityRef(id)
              .ask(ShoppingCartCommand.Checkout::new, askTimeout)
          );
    }

    private CompletionStage<Done> convertOperationResult(CompletionStage<OperationResult> future) {
        return future.exceptionally(ex -> {
            // TimeoutException from ask
            throw new BadRequest("Error updating shopping cart");
        })
          .thenApply(result -> {
              if (result instanceof ShoppingCartCommand.Rejected)
                  throw new BadRequest(((ShoppingCartCommand.Rejected) result).reason);
              return Done.getInstance();
          });
    }

    private ShoppingCart convertShoppingCart(String id, ShoppingCartState cart) {
        List<ShoppingCartItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> item : cart.getItems().entrySet()) {
            items.add(new ShoppingCartItem(item.getKey(), item.getValue()));
        }
        return new ShoppingCart(id, items, cart.isCheckedOut());
    }

}
