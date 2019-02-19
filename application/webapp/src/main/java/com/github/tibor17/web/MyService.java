package com.github.tibor17.web;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

@ApplicationScoped
public class MyService {
    @PersistenceContext(unitName = "mt")
    private EntityManager em;

    @Inject
    private Producer producer;

    @Transactional
    public void saveTenant1(String reason) {
        MyEntity a = new MyEntity();
        a.setName("schema-1 " + reason);
        producer.send("Hi There!");
        em.persist(a);
    }

    @Transactional
    public void saveTenant2(String reason) {
        MyEntity a = new MyEntity();
        a.setName("schema-2 " + reason);
        em.persist(a);
    }
}
