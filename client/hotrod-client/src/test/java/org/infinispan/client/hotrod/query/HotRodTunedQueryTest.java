package org.infinispan.client.hotrod.query;

import org.hibernate.search.engine.spi.SearchFactoryImplementor;
import org.hibernate.search.indexes.spi.IndexManager;
import org.infinispan.commons.equivalence.ByteArrayEquivalence;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.Index;
import org.infinispan.query.remote.indexing.ProtobufValueWrapper;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.CleanupAfterMethod;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertTrue;

import java.io.File;

/**
 * Verifying that the tuned query configuration also works for Remote Queries.
 *
 * @author Anna Manukyan
 */
@Test(testName = "client.hotrod.query.HotRodTunedQueryTest", groups = "functional")
@CleanupAfterMethod
public class HotRodTunedQueryTest extends RemoteQueryDslConditionsTest {

   private final String indexDirectory = TestingUtil.tmpDirectory(getClass());

   @Override
   protected ConfigurationBuilder getConfigurationBuilder() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.dataContainer()
            .keyEquivalence(ByteArrayEquivalence.INSTANCE)
            .valueEquivalence(ByteArrayEquivalence.INSTANCE)
            .indexing().index(Index.ALL)
            .addProperty("default.indexmanager", "near-real-time")
            .addProperty("default.indexBase", indexDirectory)
            .addProperty("default.exclusive_index_use", "true")
            .addProperty("default.indexwriter.merge_factor", "30")
            .addProperty("default.indexwriter.merge_max_size", "4096")
            .addProperty("default.indexwriter.ram_buffer_size", "220")
            .addProperty("default.locking_strategy", "native")
            .addProperty("default.sharding_strategy.nbr_of_shards", "6")
            .addProperty("lucene_version", "LUCENE_48");

      return builder;
   }

   @Override
   public void testIndexPresence() {
      SearchFactoryImplementor searchFactory = (SearchFactoryImplementor) org.infinispan.query.Search.getSearchManager(cache).getSearchFactory();
      for(IndexManager manager :  searchFactory.getIndexManagerHolder().getIndexManagers()) {
         assertTrue(manager.getIndexName().contains(ProtobufValueWrapper.class.getName()));
      }
   }

   @Override
   protected void setup() throws Exception {
      TestingUtil.recursiveFileRemove(indexDirectory);
      boolean created = new File(indexDirectory).mkdirs();
      assertTrue(created);
      super.setup();
   }

   @Override
   protected void teardown() {
      try {
         //first stop cache managers, then clear the index
         super.teardown();
      } finally {
         //delete the index otherwise it will mess up the index for next tests
         TestingUtil.recursiveFileRemove(indexDirectory);
      }
   }
}
