package com.example.shoppingcart.impl;

import akka.actor.typed.ActorRef;
import akka.persistence.typed.ExpectingReply;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.serialization.Jsonable;
import lombok.Value;

/**
 * This interface defines all the commands that the {@link ShoppingCartEntity} supports.
 * <p>
 * By convention, the commands should be inner classes of the interface, which
 * makes it simple to get a complete picture of what commands an entity
 * supports.
 */
public interface ShoppingCartCommand<ReplyType> extends Jsonable, ExpectingReply<ReplyType> {
    /**
     * A command to update an item.
     *
     * It has a reply type of {@link OperationResult}, which is sent back to the caller
     * when all the events emitted by this command are successfully persisted.
     */
    @SuppressWarnings("serial")
    @Value
    @JsonDeserialize
    final class UpdateItem implements ShoppingCartCommand<OperationResult>, CompressedJsonable {
        public final String productId;
        public final int quantity;
        private final ActorRef<OperationResult> replyTo;

        @JsonCreator
        UpdateItem(String productId, int quantity, ActorRef<OperationResult> replyTo) {
            this.productId = Preconditions.checkNotNull(productId, "productId");
            this.quantity = quantity;
            this.replyTo = replyTo;
        }

        @Override
        public ActorRef<OperationResult> replyTo() {
            return replyTo;
        }
    }

    /**
     * A command to get the current state of the shopping cart.
     *
     * The reply type is the {@link ShoppingCartState}
     */
    final class Get implements ShoppingCartCommand<ShoppingCartState> {
        private final ActorRef<ShoppingCartState> replyTo;

        @JsonCreator
        public Get(ActorRef<ShoppingCartState> replyTo) {
            this.replyTo = replyTo;
        }

        @Override
        public ActorRef<ShoppingCartState> replyTo() {
            return replyTo;
        }
    }

    /**
     * A command to checkout the shopping cart.
     *
     * The reply type is the {@link OperationResult}, which will be returned when the events have been
     * emitted.
     */
    class Checkout implements ShoppingCartCommand<OperationResult> {
        private final ActorRef<OperationResult> replyTo;

        public Checkout(ActorRef<OperationResult> replyTo) {
            this.replyTo = replyTo;
        }

        @Override
        public ActorRef<OperationResult> replyTo() {
            return replyTo;
        }
    }

    interface OperationResult {}

    enum Confirmed implements OperationResult {
        INSTANCE
    }

    class Rejected implements OperationResult {
        public final String reason;

        public Rejected(String reason) {
            this.reason = reason;
        }
    }
}
