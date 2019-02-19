package com.github.tibor17;

import bitronix.tm.resource.ResourceRegistrar;
import bitronix.tm.resource.jdbc.lrc.LrcXADataSource;
import org.h2.Driver;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.SessionFactoryImpl;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;
import javax.transaction.UserTransaction;

import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.hibernate.resource.transaction.backend.jta.internal.JtaTransactionCoordinatorImpl;
import org.infinispan.hibernate.cache.v53.InfinispanRegionFactory;
import org.hibernate.internal.SessionFactoryImpl;
import org.infinispan.manager.DefaultCacheManager;

import bitronix.tm.resource.ResourceRegistrar;
//import bitronix.tm.resource.infinispan.InfinispanCacheManager;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import org.infinispan.manager.EmbeddedCacheManager;


/**
 * name="hibernate.transaction.jta.platform" value="org.hibernate.service.jta.platform.internal.BitronixJtaPlatform"
 */
public class JTAExampleBTM {

    /*public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "bitronix.tm.jndi.BitronixInitialContextFactory");
        // Attention: BitronixInitialContextFactory isn't a real jndi implementation: you can't do explicit bindings
        // It is ideal for hibernate standalone usage, as it automatically 'binds' the needed things: datasource + usertransaction

        InitialContext ictx = new InitialContext(props);

        PoolingDataSource myDataSource = new PoolingDataSource();
        myDataSource.setClassName(LrcXADataSource.class.getName());
        myDataSource.setMaxPoolSize(5);
        myDataSource.setAllowLocalTransactions(true);
        myDataSource.getDriverProperties().setProperty("driverClassName", Driver.class.getName());
        myDataSource.getDriverProperties().setProperty("url", "jdbc:h2:mem:test");
        myDataSource.getDriverProperties().setProperty("user", "sa");
        myDataSource.getDriverProperties().setProperty("password", "");
        myDataSource.setUniqueName("java:/JavaEEMTDS");
        myDataSource.setAutomaticEnlistingEnabled(true); // important to keep it to true (default), otherwise commits/rollbacks are not propagated
        myDataSource.init(); // does also register the datasource on the Fake-JNDI with Unique Name

        org.hibernate.transaction.BTMTransactionManagerLookup lokhiberante = new org.hibernate.transaction.BTMTransactionManagerLookup();

        SessionFactoryImplementor emf = (SessionFactoryImplementor) Persistence.createEntityManagerFactory("multi-tenancy");
        InfinispanRegionFactory infinispanregionfactory = (InfinispanRegionFactory) emf.getSettings().getRegionFactory();
        EmbeddedCacheManager manager = infinispanregionfactory.getCacheManager();

        // register Inifinispan as a BTM resource
        InfinispanCacheManager icm = new InfinispanCacheManager();
        icm.setUniqueName("infinispan");
        ResourceRegistrar.register(icm);
        icm.setManager(manager);

        final UserTransaction userTransaction = (UserTransaction) ictx.lookup(lokhiberante.getUserTransactionName());

        // begin a new Transaction
        userTransaction.begin();
        EntityManager em = emf.createEntityManager();

        A a = new A();
        a.setName("firstvalue");
        em.persist(a);
        em.flush();     // do manually flush here as apparently FLUSH_BEFORE_COMPLETION seems not work, bug ?

        System.out.println("Calling userTransaction.commit() (Please check if the commit is effectively executed!)");
        userTransaction.commit();

        emf.close();
    }*/
}
