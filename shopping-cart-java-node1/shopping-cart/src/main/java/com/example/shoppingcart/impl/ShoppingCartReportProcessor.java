package com.example.shoppingcart.impl;

import com.google.common.collect.ImmutableMap;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;

public class ShoppingCartReportProcessor extends ReadSideProcessor<ShoppingCartEvent> {

    private final JpaReadSide jpaReadSide;


    @Inject
    public ShoppingCartReportProcessor(JpaReadSide jpaReadSide) {
        this.jpaReadSide = jpaReadSide;
    }


    @Override
    public ReadSideHandler<ShoppingCartEvent> buildHandler() {
        return jpaReadSide
                .<ShoppingCartEvent>builder("shopping-cart-report")
                .setGlobalPrepare(this::createSchema)
                .setEventHandler(ShoppingCartEvent.ItemUpdated.class, this::createReport)
                .setEventHandler(ShoppingCartEvent.CheckedOut.class, this::addCheckoutTime)
                .build();
    }

    private void createSchema(@SuppressWarnings("unused") EntityManager ignored) {
        Persistence.generateSchema("default", ImmutableMap.of("hibernate.hbm2ddl.auto", "update"));
    }

    private void createReport(EntityManager entityManager, ShoppingCartEvent.ItemUpdated evt) {
        if (findReport(entityManager, evt.shoppingCartId) == null) {
            ShoppingCartReport report = new ShoppingCartReport();
            report.setId(evt.shoppingCartId);
            report.setCreationDate(evt.eventTime);
            entityManager.persist(report);
        }
    }

    private void addCheckoutTime(EntityManager entityManager, ShoppingCartEvent.CheckedOut evt) {
        ShoppingCartReport report = findReport(entityManager, evt.shoppingCartId);
        if (report != null) {
            report.setCheckoutDate(evt.eventTime);
            entityManager.persist(report);
        } else {
            throw new RuntimeException("Didn't find cart for checkout. CartID: " + evt.shoppingCartId);
        }
    }
    private ShoppingCartReport findReport(EntityManager entityManager, String cartId) {
        return entityManager.find(ShoppingCartReport.class, cartId);
    }


    @Override
    public PSequence<AggregateEventTag<ShoppingCartEvent>> aggregateTags() {
        return TreePVector.singleton(ShoppingCartEvent.TAG);
    }

}
