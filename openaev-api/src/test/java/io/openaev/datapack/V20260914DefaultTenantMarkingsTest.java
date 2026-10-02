package io.openaev.datapack;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.processor.MigrationProcessor;
import io.openaev.processor.datapack.V20260914_Default_tenant_markings;
import io.openaev.service.DataPackService;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Deliberately NOT {@code @Transactional}: same reasoning as {@link MigrationProcessorTest} —
 * {@link MigrationProcessor} opens its own tenant-scoped background transaction per {@code (tenant,
 * processable)} via {@link TenantScopedTransaction#execute}, which refuses to run inside an
 * already-active transaction. Seed and clean up through committed writes instead.
 */
public class V20260914DefaultTenantMarkingsTest extends IntegrationTest {

  @Autowired private DataPackService dataPackService;
  @Autowired private V20260914_Default_tenant_markings markingsDataPack;
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
              .createNativeQuery("DELETE FROM marking_definitions WHERE tenant_id = ?1")
              .setParameter(1, tenant.getId())
              .executeUpdate();
          entityManager
              .createNativeQuery("DELETE FROM datapacks WHERE datapack_id = ?1 AND tenant_id = ?2")
              .setParameter(1, markingsDataPack.getPackId())
              .setParameter(2, tenant.getId())
              .executeUpdate();
          return null;
        });
  }

  @Test
  @DisplayName(
      "given_oneSeedAlreadyPresentFromAFailedRun_should_completeRemainingSeedsAndRegisterPack")
  void given_oneSeedAlreadyPresentFromAFailedRun_should_completeRemainingSeedsAndRegisterPack()
      throws Exception {
    Tenant tenant = new Tenant(TenantContext.getCurrentTenant());

    // Arrange: simulate a prior partial run that inserted the first seed (TLP:CLEAR), then
    // crashed before the pack could be registered as processed (so the next startup retries it).
    tenantTx.execute(
        TxCtx.forTenant(tenant.getId()),
        () -> {
          entityManager
              .createNativeQuery(
                  """
                  INSERT INTO marking_definitions
                    (marking_definition_id, marking_definition_type, marking_definition_definition,
                     marking_definition_color, marking_definition_order,
                     marking_definition_protected, tenant_id)
                  VALUES (?1, 'TLP', 'TLP:CLEAR', '#E6E7E8', 1, true, ?2)
                  """)
              .setParameter(1, UUID.randomUUID().toString())
              .setParameter(2, tenant.getId())
              .executeUpdate();
          return null;
        });

    MigrationProcessor processor =
        new MigrationProcessor(
            List.of(markingsDataPack), Collections.emptyList(), tenantRepository, tenantTx);

    // Act: retry the pack — with an idempotent find-or-create this must not fail on the unique
    // index and must converge (create the 4 remaining seeds, then register the pack as done).
    processor.createDependencyForTenant(tenant);

    // Assert
    assertThat(dataPackService.findByIdAndTenant(markingsDataPack.getPackId(), tenant)).isPresent();

    Number count =
        (Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM marking_definitions WHERE tenant_id = ?1")
                .setParameter(1, tenant.getId())
                .getSingleResult();
    assertThat(count.intValue()).isEqualTo(5);
  }
}
