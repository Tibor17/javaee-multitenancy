package com.github.tibor17;

import bitronix.tm.BitronixTransaction;
import bitronix.tm.BitronixTransactionManager;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.utils.DefaultExceptionAnalyzer;
import junit.framework.TestCase;

import javax.transaction.*;

import bitronix.tm.resource.jdbc.PoolingDataSource;
import bitronix.tm.resource.jdbc.lrc.LrcXADataSource;

import java.sql.Connection;

import org.h2.Driver;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class JtaTest extends TestCase {

    private final static Logger log = LoggerFactory.getLogger(JtaTest.class);

    private BitronixTransactionManager btm;

    protected void setUp() throws Exception {
        TransactionManagerServices.getConfiguration().setGracefulShutdownInterval(1);
        TransactionManagerServices.getConfiguration().setExceptionAnalyzer(DefaultExceptionAnalyzer.class.getName());
        btm = TransactionManagerServices.getTransactionManager();
    }

    protected void tearDown() throws Exception {
        btm.shutdown();
    }

    public void testTransactionManagerGetTransaction() throws Exception {
        assertNull(btm.getTransaction());

        btm.begin();
        assertNotNull(btm.getTransaction());

        btm.commit();
        assertNull(btm.getTransaction());

        btm.begin();
        assertNotNull(btm.getTransaction());

        btm.rollback();
    }

    // this test also helps verifying MDC support but logs have to be manually checked
    public void testSuspendResume() throws Exception {
        log.info("test starts");
        btm.begin();
        log.info("tx begun");
        Transaction tx = btm.suspend();
        log.info("tx suspended");
        btm.resume(tx);
        log.info("tx resumed");
        btm.rollback();
        log.info("test over");
    }

    public void testTimeout() throws Exception {
        btm.setTransactionTimeout(1);
        btm.begin();
        CountingSynchronization sync = new CountingSynchronization();
        btm.getTransaction().registerSynchronization(sync);

        Thread.sleep(2000);
        assertEquals(Status.STATUS_MARKED_ROLLBACK, btm.getTransaction().getStatus());

        try {
            btm.commit();
            fail("commit should have thrown an RollbackException");
        } catch (RollbackException ex) {
            assertEquals("transaction timed out and has been rolled back", ex.getMessage());
        }
        assertEquals(1, sync.beforeCount);
        assertEquals(1, sync.afterCount);
    }

    public void testMarkedRollback() throws Exception {
        btm.begin();
        CountingSynchronization sync = new CountingSynchronization();
        btm.getTransaction().registerSynchronization(sync);
        btm.setRollbackOnly();

        assertEquals(Status.STATUS_MARKED_ROLLBACK, btm.getTransaction().getStatus());

        try {
            btm.commit();
            fail("commit should have thrown an RollbackException");
        } catch (RollbackException ex) {
            assertEquals("transaction was marked as rollback only and has been rolled back", ex.getMessage());
        }
        assertEquals(1, sync.beforeCount);
        assertEquals(1, sync.afterCount);
    }

    public void testRecycleAfterSuspend() throws Exception {
        PoolingDataSource pds = new PoolingDataSource();
        pds.setClassName(LrcXADataSource.class.getName());
        pds.setMinPoolSize(1);
        pds.setMaxPoolSize(2);
        pds.setAllowLocalTransactions(true);
        pds.setUniqueName("java:/MyDatasource");
        pds.getDriverProperties().setProperty("driverClassName", Driver.class.getName());
        pds.getDriverProperties().setProperty("url", "jdbc:h2:mem:test");
        pds.getDriverProperties().setProperty("user", "sa");
        pds.getDriverProperties().setProperty("password", "");
        pds.setAutomaticEnlistingEnabled(true);
        pds.init();

        btm.begin();

        Connection c1 = pds.getConnection();
        c1.createStatement();
        c1.close();

        Transaction tx = btm.suspend();

        btm.begin();

        Connection c11 = pds.getConnection();
        c11.createStatement();
        c11.close();

        btm.commit();


        btm.resume(tx);

        Connection c2 = pds.getConnection();
        c2.createStatement();
        c2.close();

        btm.commit();

        pds.close();
    }

    public void testTransactionContextCleanup() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, btm.getStatus());

        btm.begin();
        assertEquals(Status.STATUS_ACTIVE, btm.getStatus());

        final Transaction tx = btm.getTransaction();

        // commit on a different thread
        Thread t = new Thread() {
            public void run() {
                try {
                    tx.commit();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    fail();
                }
            }
        };

        t.start();
        t.join();

        assertNull(btm.getTransaction());
    }

    public void testBeforeCompletionAddsExtraSynchronizationInDifferentPriority() throws Exception {
        btm.begin();

        btm.getCurrentTransaction().getSynchronizationScheduler().add(new SynchronizationRegisteringSynchronization(btm.getCurrentTransaction()), 5);

        btm.commit();
    }

    public void testBeforeCompletionRuntimeExceptionRethrown() throws Exception {
        btm.begin();

        btm.getTransaction().registerSynchronization(new Synchronization() {
            public void beforeCompletion() {
                throw new RuntimeException("beforeCompletion failure");
            }
            public void afterCompletion(int i) {
            }
        });

        try {
            btm.commit();
            fail("expected runtime exception");
        } catch (RollbackException ex) {
            assertEquals(RuntimeException.class, ex.getCause().getClass());
            assertEquals("beforeCompletion failure", ex.getCause().getMessage());
        }

        btm.begin();
        btm.commit();
    }

    private class SynchronizationRegisteringSynchronization implements Synchronization {

        private BitronixTransaction transaction;

        public SynchronizationRegisteringSynchronization(BitronixTransaction transaction) {
            this.transaction = transaction;
        }

        public void beforeCompletion() {
            try {
                transaction.getSynchronizationScheduler().add(new Synchronization() {

                    public void beforeCompletion() {
                    }

                    public void afterCompletion(int i) {
                    }
                }, 10);
            } catch (Exception e) {
                fail("unexpected exception: " + e);
            }
        }

        public void afterCompletion(int i) {
        }
    }

    private class CountingSynchronization implements Synchronization {
        public int beforeCount = 0;
        public int afterCount = 0;

        public void beforeCompletion() {
            beforeCount++;
        }

        public void afterCompletion(int i) {
            afterCount++;
        }
    }

}
