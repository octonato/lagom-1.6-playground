package com.example.shoppingcart.api;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import static com.lightbend.lagom.javadsl.api.Service.*;

/**
 * The shopping cart service interface.
 * <p>
 * This describes everything that Lagom needs to know about how to serve and
 * consume the ShoppingCartService.
 */
public interface ShoppingCartService extends Service {


    /**
     * Get a shopping cart.
     * <p>
     * Example: curl http://localhost:9000/shoppingcart/123
     */
    ServiceCall<NotUsed, ShoppingCart> get(String id);


    /**
     * Get a shopping cart report (view model).
     *
     * Example: curl http://localhost:9000/shoppingcart/123/report
     */
    ServiceCall<NotUsed, ShoppingCartReportView> getReport(String id);

    /**
     * Update an items quantity in the shopping cart.
     * <p>
     * Example: curl -H "Content-Type: application/json" -X POST -d '{"productId": 456, "quantity": 2}' http://localhost:9000/shoppingcart/123
     */
    ServiceCall<ShoppingCartItem, Done> updateItem(String id);

    /**
     * Checkout the shopping cart.
     * <p>
     * Example: curl -X POST http://localhost:9000/shoppingcart/123/checkout
     */
    ServiceCall<NotUsed, Done> checkout(String id);

    @Override
    default Descriptor descriptor() {
        return named("shopping-cart")
            .withCalls(
                restCall(Method.GET, "/shoppingcart/:id", this::get),
                restCall(Method.GET, "/shoppingcart/:id/report", this::getReport),
                restCall(Method.POST, "/shoppingcart/:id", this::updateItem),
                restCall(Method.POST, "/shoppingcart/:id/checkout", this::checkout)
            )
            .withAutoAcl(true);
    }
}
