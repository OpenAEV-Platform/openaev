package io.openaev.datapack;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.datapack.local_fixtures.TestDataPack;
import io.openaev.processor.MigrationProcessor;
import io.openaev.service.DataPackService;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Deliberately NOT {@code @Transactional}: {@link MigrationProcessor} now opens its own
 * tenant-scoped background transaction per {@code (tenant, processable)} via {@link
 * TenantScopedTransaction#execute}, which refuses to run inside an already-active transaction. Seed
 * and clean up through committed writes instead, matching {@code
 * TenantScopedTransactionIntegrationTest}.
 *
 * <p>Exercises {@link MigrationProcessor#createDependencyForTenant} (a single, explicit tenant)
 * rather than {@link MigrationProcessor#process()} (loops over every active tenant in the DB): this
 * test's DB is long-lived and may carry other tenants left over from other suites, and {@code
 * process()} would process all of them, leaving {@code TenantContext}'s thread-local pointed at
 * whichever tenant ran last instead of the one this test cares about.
 */
public class MigrationProcessorTest extends IntegrationTest {
  @Autowired private DataPackService dataPackService;
  @Autowired private TestDataPack testDataPack;
  @Autowired private TagRepository tagRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EntityManager entityManager;

  @AfterEach
  void cleanup() {
    Tenant tenant = new Tenant(TenantContext.getCurrentTenant());
    tenantTx.execute(
        TxCtx.forTenant(tenant.getId()),
        () -> {
          entityManager
              .createNativeQuery("DELETE FROM tags WHERE tag_name = ?1 AND tenant_id = ?2")
              .setParameter(1, testDataPack.tagName)
              .setParameter(2, tenant.getId())
              .executeUpdate();
          entityManager
              .createNativeQuery("DELETE FROM datapacks WHERE datapack_id = ?1 AND tenant_id = ?2")
              .setParameter(1, testDataPack.getPackId())
              .setParameter(2, tenant.getId())
              .executeUpdate();
          return null;
        });
  }

  @Test
  @DisplayName("Processor processes all known datapacks")
  public void processorProcessesAllKnownDatapacks() throws Exception {
    MigrationProcessor processor =
        new MigrationProcessor(
            List.of(testDataPack), Collections.emptyList(), tenantRepository, tenantTx);
    Tenant tenant = new Tenant(TenantContext.getCurrentTenant());

    // act
    processor.createDependencyForTenant(tenant);

    // assert
    assertThat(dataPackService.findByIdAndTenant(TestDataPack.class.getCanonicalName(), tenant))
        .isPresent();
    assertThat(tagRepository.findByNameAndTenantId(testDataPack.tagName, tenant.getId()))
        .isPresent();
  }

  @Test
  @DisplayName("Already processed datapacks don't process again")
  public void alreadyProcessedDatapackDontProcessAgain() throws Exception {
    MigrationProcessor processor =
        new MigrationProcessor(
            List.of(testDataPack), Collections.emptyList(), tenantRepository, tenantTx);
    Tenant tenant = new Tenant(TenantContext.getCurrentTenant());
    // fake registering the data pack
    dataPackService.registerDataPack(testDataPack.getPackId(), tenant);

    // act
    processor.createDependencyForTenant(tenant);

    // assert
    assertThat(dataPackService.findByIdAndTenant(TestDataPack.class.getCanonicalName(), tenant))
        .isPresent();
    // not that we prevented the pack from processing so we shouldn't find the contents in db
    assertThat(tagRepository.findByNameAndTenantId(testDataPack.tagName, tenant.getId())).isEmpty();
  }
}
