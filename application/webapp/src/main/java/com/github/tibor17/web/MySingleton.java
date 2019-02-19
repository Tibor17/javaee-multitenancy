package com.github.tibor17.web;

import com.github.tibor17.holder.SchemaHolderSingleton;
import org.hibernate.engine.spi.SessionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.sql.SQLException;

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class MySingleton {
    private static final Logger LOG = LoggerFactory.getLogger(MySingleton.class);

    @Inject
    private MyService service;

    @PersistenceContext(unitName = "mt")
    private EntityManager em;

    @PostConstruct
    private void init() {
        String tenant = SchemaHolderSingleton.getInstance().getIdentifier();
        LOG.info("list of default tenant \"{}\"", tenant);

        SchemaHolderSingleton.getInstance().setIdentifier("schema1");
        tenant = SchemaHolderSingleton.getInstance().getIdentifier();
        LOG.info("the list of tenant \"{}\"", tenant);

        try {
            em.unwrap(SessionImplementor.class)
                    .connection()
                    .setSchema(tenant);

            em.unwrap(SessionImplementor.class)
                    .connection().createStatement()
                    .execute("SET SCHEMA '" + tenant + "'");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        service.saveTenant1("@PostConstruct");

        SchemaHolderSingleton.getInstance().setIdentifier("schema2");
        tenant = SchemaHolderSingleton.getInstance().getIdentifier();
        LOG.info("the list of tenant \"{}\"", tenant);
        //service.saveTenant2();
    }
}
