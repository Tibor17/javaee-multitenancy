package com.github.tibor17;

import bitronix.tm.BitronixTransactionManager;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import bitronix.tm.resource.jdbc.lrc.LrcXADataSource;
import bitronix.tm.utils.DefaultExceptionAnalyzer;
import com.scheidtbachmann.tsupportjee.mock.jpa.InjectableRunner;
import com.scheidtbachmann.tsupportjee.mock.jpa.Transactions;
import org.assertj.core.api.Assertions;
import org.h2.Driver;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.naming.Context;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceProperty;
import javax.transaction.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import static com.scheidtbachmann.tsupportjee.mock.jpa.Transactions.$;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(InjectableRunner.class)
@PersistenceContext(unitName = "multi-tenancy", properties = {
        //@PersistenceProperty(name = "hibernate.default_schema", value = "PUBLIC"), do not use. H2 always sticks to PUBLIC
        @PersistenceProperty(name = "javax.persistence.jdbc.url", value = "jdbc:h2:~/TEST;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS S2\\;CREATE SCHEMA IF NOT EXISTS S1"),
        @PersistenceProperty(name = "hibernate.hbm2ddl.auto", value = "none"),
        @PersistenceProperty(name = "hibernate.transaction.jta.platform", value = "org.hibernate.service.jta.platform.internal.BitronixJtaPlatform"),
        @PersistenceProperty(name = "hibernate.transaction.coordinator_class", value = "jta"),//org.hibernate.resource.transaction.backend.jta.internal.JtaTransactionCoordinatorImpl
        @PersistenceProperty(name = "javax.persistence.transactionType", value = "JTA"),
        @PersistenceProperty(name = "hibernate.transaction.factory_class", value = "org.hibernate.engine.transaction.jta.platform.internal.BitronixJtaPlatform"),
        @PersistenceProperty(name = "hibernate.multiTenancy", value = "SCHEMA"),
        @PersistenceProperty(name = "hibernate.tenant_identifier_resolver", value = "com.github.tibor17.SchemaResolver"),
        @PersistenceProperty(name = "hibernate.multi_tenant_connection_provider", value = "com.github.tibor17.MultiTenantProvider"),
})
public class MyTest {
    @Inject
    private EntityManager em;

    @BeforeClass
    public static void bindDataSourceToContext() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        new SchemaResolver().setTenantIdentifier("S1");
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "bitronix.tm.jndi.BitronixInitialContextFactory");
        PoolingDataSource myDataSource = new PoolingDataSource();
        myDataSource.setClassName(LrcXADataSource.class.getName());
        myDataSource.setMaxPoolSize(5);
        myDataSource.setAllowLocalTransactions(true);
        myDataSource.getDriverProperties().setProperty("driverClassName", Driver.class.getName());
        myDataSource.getDriverProperties().setProperty("url", "jdbc:h2:~/TEST;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS S2\\;CREATE SCHEMA IF NOT EXISTS S1");//"jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS S1"
        myDataSource.getDriverProperties().setProperty("user", "sa");
        myDataSource.getDriverProperties().setProperty("password", "");
        myDataSource.setUniqueName("java:comp/env/jdbc/JavaEEMTDS");
        myDataSource.setAutomaticEnlistingEnabled(true); // important to keep it to true (default), otherwise commits/rollbacks are not propagated
        myDataSource.init(); // does also register the datasource on the Fake-JNDI with Unique Name


        BitronixTransactionManager btm = TransactionManagerServices.getTransactionManager();

        Connection connection = myDataSource.getConnection();
        connection.setSchema("S1");
        btm.begin();
        connection.prepareStatement("drop table A if exists").execute();
        btm.commit();
        btm.begin();
        connection.prepareStatement("create table A (\n" +
                "       ID bigint generated by default as identity,\n" +
                "        name varchar(255),\n" +
                "        primary key (ID)\n" +
                "    )").execute();
        btm.commit();

        connection.setSchema("S2");
        btm.begin();
        connection.prepareStatement("drop table A if exists").execute();
        btm.commit();
        btm.begin();
        connection.prepareStatement("create table A (\n" +
                "       ID bigint generated by default as identity,\n" +
                "        name varchar(255),\n" +
                "        primary key (ID)\n" +
                "    )").execute();
        btm.commit();
    }

    @Test
    public void t() throws Exception {
        /*$(em -> em.createNativeQuery("CREATE SCHEMA s1"));
        $(em -> em.createNativeQuery("CREATE SCHEMA IF NOT EXISTS s2"));
        $(em -> em.createNativeQuery("CREATE SCHEMA IF NOT EXISTS s3"));*/

        em.unwrap(SessionImplementor.class).close();

        BitronixTransactionManager btm = TransactionManagerServices.getTransactionManager();

        /*
         * Separate SQL INSERT for SCHEMA S1 and S2
         **/

        new SchemaResolver().setTenantIdentifier("S1");

        btm.begin();
        A insertedEntity1 = new A();
        insertedEntity1.setName("entity object under schema 's1'");
        em.persist(insertedEntity1);
        assertThat(em.unwrap(SessionImplementor.class).connection().getSchema())
                .isEqualToIgnoringCase("S1");
        btm.commit();

        em.unwrap(SessionImplementor.class).close();

        new SchemaResolver().setTenantIdentifier("S2");

        btm.begin();
        A insertedEntity2 = new A();
        insertedEntity2.setName("entity object under schema 's2'");
        em.persist(insertedEntity2);
        assertThat(em.unwrap(SessionImplementor.class).connection().getSchema())
                .isEqualToIgnoringCase("S2");
        btm.commit();

        /*
         * Separate SQL SELECT for SCHEMA S1 and S2
         */

        /*$(() -> {
            List<A> records = em.createQuery("select a from A a", A.class)
                    .getResultList();

            assertThat(records)
                    .hasSize(1);

            A selectedEntity2 = records.get(0);

            assertThat(selectedEntity2.getId())
                    .isEqualTo(insertedEntity2.getId());

            assertThat(selectedEntity2.hashCode())
                    .isNotEqualTo(insertedEntity2.hashCode());

            assertThat(selectedEntity2.getName())
                    .isEqualTo(insertedEntity2.getName());
        });*/

        new SchemaResolver().setTenantIdentifier("S1");

        String schema = em.unwrap(SessionImplementor.class).connection().getSchema();
        ResultSet set = em.unwrap(SessionImplementor.class).connection().createStatement().executeQuery("select * from A");


        $(em -> {
            List<A> records = em.createQuery("select a from A a", A.class)
                    .getResultList();

            assertThat(records)
                    .hasSize(1);

            A selectedEntity1 = records.get(0);

            assertThat(selectedEntity1.getId())
                    .isEqualTo(insertedEntity1.getId());

            assertThat(selectedEntity1.hashCode())
                    .isNotEqualTo(insertedEntity1.hashCode());

            assertThat(selectedEntity1.getName())
                    .isEqualTo(insertedEntity1.getName());
        });
    }
}