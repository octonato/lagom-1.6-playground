package com.example.shoppingcart.api

import java.time.Instant

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, Json}
import play.api.libs.json.JsValue

object ShoppingCartService  {
  val TOPIC_NAME = "shopping-cart"
}

/**
  * The ShoppingCart service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the ShoppingCartService.
  */
trait ShoppingCartService extends Service {

  /**
    * Get a shopping cart.
    *
    * Example: curl http://localhost:9000/shoppingcart/123
    */
  def get(id: String): ServiceCall[NotUsed, ShoppingCart]

  def projectionsState(): ServiceCall[NotUsed, JsValue]
  def projectionState(projectionName: String): ServiceCall[NotUsed, JsValue]
  def startAllWorker(projectionName: String): ServiceCall[NotUsed, NotUsed]
  def startWorker(projectionName: String, workerName: String): ServiceCall[NotUsed, NotUsed]
  def stopAllWorker(projectionName: String): ServiceCall[NotUsed, NotUsed]
  def stopWorker(projectionName: String, workerName: String): ServiceCall[NotUsed, NotUsed]
  /**
   * Get a shopping cart report (view model).
   *
   * Example: curl http://localhost:9000/shoppingcart/123/report
   */
  def getReport(id: String): ServiceCall[NotUsed, ShoppingCartReport]

  /**
    * Update an items quantity in the shopping cart.
    *
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"productId": 456, "quantity": 2}' http://localhost:9000/shoppingcart/123
    */
  def updateItem(id: String): ServiceCall[ShoppingCartItem, Done]

  /**
    * Checkout the shopping cart.
    *
    * Example: curl -X POST http://localhost:9000/shoppingcart/123/checkout
    */
  def checkout(id: String): ServiceCall[NotUsed, Done]


  override final def descriptor = {
    import Service._
    // @formatter:off
    named("shopping-cart")
      .withCalls(
        restCall(Method.GET, "/shoppingcart/:id", get _),
        restCall(Method.GET, "/shoppingcart/:id/report", getReport _),
        restCall(Method.POST, "/shoppingcart/:id", updateItem _),
        restCall(Method.POST, "/shoppingcart/:id/checkout", checkout _),
        restCall(Method.GET, "/projection", projectionsState),
        restCall(Method.GET, "/projection/:name", projectionState _),
        restCall(Method.GET, "/projection/:name/start", startAllWorker _),
        restCall(Method.GET, "/projection/:name/stop", stopAllWorker _),
        restCall(Method.GET, "/projection/:name/:worker/start", startWorker _),
        restCall(Method.GET, "/projection/:name/:worker/stop", stopWorker _)
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

/**
  * A shopping cart item.
  *
  * @param productId The ID of the product.
  * @param quantity The quantity of the product.
  */
case class ShoppingCartItem(productId: String, quantity: Int)

object ShoppingCartItem {
  /**
    * Format for converting the shopping cart item to and from JSON.
    *
    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
    */
  implicit val format: Format[ShoppingCartItem] = Json.format
}

/**
  * A shopping cart.
  *
  * @param id The id of the shopping cart.
  * @param items The items in the shopping cart.
  * @param checkedOut Whether the shopping cart has been checked out (submitted).
  */
case class ShoppingCart(id: String, items: Seq[ShoppingCartItem], checkedOut: Boolean)

object ShoppingCart {

  implicit val format: Format[ShoppingCart] = Json.format
}


/**
 * A shopping cart report exposes information about a ShoppingCart.
 */
case class ShoppingCartReport(cartId: String,
                              creationDate: Instant,
                              checkoutDate: Option[Instant])

object ShoppingCartReport {
  implicit val format: Format[ShoppingCartReport] = Json.format
}

