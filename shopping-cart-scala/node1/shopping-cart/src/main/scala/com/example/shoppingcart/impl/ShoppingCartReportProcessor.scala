package com.example.shoppingcart.impl

import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import org.slf4j.LoggerFactory


class ShoppingCartReportProcessor(readSide: SlickReadSide,
                                  repository: ShoppingCartReportRepository) extends ReadSideProcessor[ShoppingCartEvent] {

  val logger = LoggerFactory.getLogger(this.getClass())

  override def buildHandler() =
    readSide
      .builder[ShoppingCartEvent]("shopping-cart-view")
      .setGlobalPrepare(repository.createTable())
      .setEventHandler[ItemUpdated] { envelope =>
        logger.info(s"got updated event: ${envelope.event}")
        repository.createReport(envelope.entityId, envelope.event.eventTime)
      }
      .setEventHandler[CheckedOut] { envelope =>
        logger.info(s"got checked-out event: ${envelope.event}")
        repository.addCheckoutTime(envelope.entityId, envelope.event.eventTime)
      }
      .build()

  override def aggregateTags = ShoppingCartEvent.Tag.allTags
}
