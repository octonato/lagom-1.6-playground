package com.example.shoppingcart.impl

import java.time.OffsetDateTime

import akka.{Done, NotUsed}
import com.example.shoppingcart.api.{ShoppingCart, ShoppingCartItem, ShoppingCartReport, ShoppingCartService}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.{BadRequest, NotFound, TransportException}
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}

import scala.concurrent.ExecutionContext
import com.lightbend.lagom.scaladsl.projection.Projections
import scala.concurrent.Future
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import com.lightbend.lagom.projection.Worker
import com.lightbend.lagom.projection.Projection

/**
  * Implementation of the `ShoppingCartService`.
  */
class ShoppingCartServiceImpl(persistentEntityRegistry: PersistentEntityRegistry, 
                              reportRepository: ShoppingCartReportRepository,
                              projections: Projections)(implicit ec: ExecutionContext) extends ShoppingCartService {

  /**
    * Looks up the shopping cart entity for the given ID.
    */
  private def entityRef(id: String) =
    persistentEntityRegistry.refFor[ShoppingCartEntity](id)

  override def get(id: String): ServiceCall[NotUsed, ShoppingCart] = ServiceCall { _ =>
    entityRef(id)
      .ask(Get)
      .map(cart => convertShoppingCart(id, cart))
  }

  override def updateItem(id: String): ServiceCall[ShoppingCartItem, Done] = ServiceCall { update =>
    entityRef(id)
      .ask(UpdateItem(update.productId, update.quantity))
      .recover {
        case ShoppingCartException(message) => throw BadRequest(message)
      }
  }

  override def checkout(id: String): ServiceCall[NotUsed, Done] = ServiceCall { _ =>
    entityRef(id)
      .ask(Checkout)
      .recover {
        case ShoppingCartException(message) => throw BadRequest(message)
      }
  }

  override def getReport(cartId: String): ServiceCall[NotUsed, ShoppingCartReport] = ServiceCall { _ =>
    reportRepository.findById(cartId).map {
      case Some(cart) => cart
      case None => throw NotFound(s"Couldn't find a shopping cart report for $cartId")
    }
  }

  private def convertShoppingCart(id: String, cart: ShoppingCartState) = {
    ShoppingCart(id, cart.items.map((ShoppingCartItem.apply _).tupled).toSeq, cart.checkedOut)
  }

  private def projectionToJson(projection: Projection): JsValue = {
    def workersToJsValue( workers: Seq[Worker]): Seq[JsValue] = {
      workers.map { worker =>  
        Json.obj(
          "name" -> worker.tagName,
          "requestedStatus" -> worker.requestedStatus.toString(),
          "observedStatus" -> worker.observedStatus.toString()
        )
      }
    }

    Json.obj(
      "name" -> projection.name, 
      "workers" -> workersToJsValue(projection.workers)
    )
  }

  override def projectionState(name: String): ServiceCall[NotUsed, JsValue] = ServiceCall { _ => 
    projections.getStatus.map { state => 
      state
        .findProjection(name)
        .map { proj => projectionToJson(proj) }
        .getOrElse(throw NotFound(s"No projection found for $name"))
    }
  }

  override def projectionsState(): ServiceCall[NotUsed, JsValue] = ServiceCall { _ => 
    projections.getStatus.map { state =>
      Json.obj(
        "projections" -> state.projections.map(projectionToJson)
      )
    }
  }
  
  override def startAllWorker(projectionName: String): ServiceCall[NotUsed, NotUsed] = ServiceCall { _ => 
    projections.startAllWorkers(projectionName)
    Future.successful(NotUsed)
  }

  override def startWorker(projectionName: String, workerName: String): ServiceCall[NotUsed, NotUsed] = ServiceCall { _ => 
    projections.startWorker(projectionName, workerName)
    Future.successful(NotUsed)
  }
  override def stopAllWorker(projectionName: String): ServiceCall[NotUsed, NotUsed] = ServiceCall { _ => 
    projections.stopAllWorkers(projectionName)
    Future.successful(NotUsed)
  }

  override def stopWorker(projectionName: String, workerName: String): ServiceCall[NotUsed, NotUsed] = ServiceCall { _ => 
    projections.stopWorker(projectionName, workerName)
    Future.successful(NotUsed)
  }
}
