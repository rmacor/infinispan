package org.infinispan.tx.recovery;

import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.transaction.lookup.DummyTransactionManagerLookup;
import org.infinispan.transaction.tm.DummyTransaction;
import org.infinispan.transaction.tm.DummyTransactionManager;
import org.infinispan.transaction.xa.TransactionXaAdapter;
import org.testng.annotations.Test;

import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;

import static org.infinispan.tx.recovery.RecoveryTestUtil.*;
import static org.testng.Assert.assertEquals;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test (testName = "tx.recovery.LocalRecoveryTest")
public class LocalRecoveryTest extends SingleCacheManagerTest {

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      EmbeddedCacheManager cm = TestCacheManagerFactory.createLocalCacheManager();
      cm.getDefaultConfiguration().configureTransaction().transactionManagerLookupClass(DummyTransactionManagerLookup.class);
      cm.getDefaultConfiguration().configureTransaction().configureRecovery().enabled(true);
      cm.getDefaultConfiguration().configureLocking().useLockStriping(false);
      return cm;
   }

   public void testOneTx() throws Exception {
      dummyTm().begin();
      cache.put("k", "v");
      TransactionXaAdapter xaRes = (TransactionXaAdapter) dummyTm().firstEnlistedResource();
      assertPrepared(0, dummyTm().getTransaction());
      xaRes.prepare(xaRes.getLocalTransaction().getXid());
      assertPrepared(1, dummyTm().getTransaction());
      xaRes.commit(xaRes.getLocalTransaction().getXid(), false);
      assertPrepared(0, dummyTm().getTransaction());
      assertEquals(0, TestingUtil.getTransactionTable(cache).getLocalTxCount());
   }

   public void testMultipleTransactions() throws Exception {
      DummyTransaction suspend1 = beginTx();
      DummyTransaction suspend2 = beginTx();
      DummyTransaction suspend3 = beginTx();
      DummyTransaction suspend4 = beginTx();

      assertPrepared(0, suspend1, suspend2, suspend3, suspend4);

      prepareTransaction(suspend1);
      assertPrepared(1, suspend1, suspend2, suspend3, suspend4);

      prepareTransaction(suspend2);
      assertPrepared(2, suspend1, suspend2, suspend3, suspend4);

      prepareTransaction(suspend3);
      assertPrepared(3, suspend1, suspend2, suspend3, suspend4);

      prepareTransaction(suspend4);
      assertPrepared(4, suspend1, suspend2, suspend3, suspend4);

      commitTransaction(suspend1);
      assertPrepared(3, suspend1, suspend2, suspend3, suspend4);

      commitTransaction(suspend2);
      assertPrepared(2, suspend1, suspend2, suspend3, suspend4);

      commitTransaction(suspend3);
      assertPrepared(1, suspend1, suspend2, suspend3, suspend4);

      commitTransaction(suspend4);
      assertPrepared(0, suspend1, suspend2, suspend3, suspend4);

      assertEquals(0, TestingUtil.getTransactionTable(cache).getLocalTxCount());
   }

   private DummyTransaction beginTx() throws NotSupportedException, SystemException {
      return beginAndSuspendTx(cache);
   }


   private DummyTransactionManager dummyTm() {
      return (DummyTransactionManager) tm();
   }
}
