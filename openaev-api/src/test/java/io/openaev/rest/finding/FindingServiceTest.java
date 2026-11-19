package io.openaev.rest.finding;

import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

// The test profile declares no active tables, so removing the v1 @Filter would leave this suite
// asserting an isolation nothing enforces. The list is not limited to findings because
// @TestPropertySource REPLACES the property rather than adding to it: naming findings alone would
// deactivate assets and asset_groups, which ARE active in production, and the suite would test less
// than production while looking stricter.
@TestPropertySource(properties = "openaev.tenant.active-tables=findings,assets,asset_groups")
@Transactional
class FindingServiceTest extends IntegrationTest {

  @Autowired private FindingService findingService;
  @Autowired private FindingRepository findingRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantScopedTransaction tenantTx;

  // Every production caller of this service reaches it with a scope already open: the queued path
  // through BatchingInjectStatusService's tenantTx.execute(TxCtx.forTenant(...)), the direct path
  // through InjectApi#injectExecutionCallback's TxCtx. Calling the service straight from a test
  // transaction reproduces neither, and the upsert's ON CONFLICT branch fails closed. Pinning the
  // ambient tenant here is what makes this suite exercise the production shape.
  @BeforeEach
  void scopeTheTestTransaction() {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(TenantContext.getCurrentTenant()));
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    // Switching tenants has to move BOTH mechanisms. The v1 thread-local and @Filter still drive
    // the injects this suite creates and the tenant TenantBaseListener stamps on a write; the v2
    // GUC is what findings now read through. Setting only the v1 side is what left this class
    // asserting an isolation nothing enforced: notBeReadableFromTenantY passed because the scope
    // was empty and NOTHING was readable, including tenant X's own rows.
    private void switchToTenant(String tenantId) {
      entityManager.flush();
      entityManager.clear();
      TenantContext.setCurrentTenant(tenantId);
      entityManager
          .unwrap(org.hibernate.Session.class)
          .enableFilter("tenantFilter")
          .setParameter("tenantId", tenantId);
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
    }

    private Finding createFindingInTenant(String tenantId) {
      String previousTenant = TenantContext.getCurrentTenant();
      try {
        switchToTenant(tenantId);

        Inject inject = getDefaultInject();
        inject = injectRepository.save(inject);

        Finding finding = new Finding();
        finding.setValue("tenant-finding-" + UUID.randomUUID());
        finding.setInject(inject);
        finding.setField("finding_field");
        finding.setType(ContractOutputType.Text);
        finding.setAssets(new ArrayList<>());

        return findingRepository.save(finding);
      } finally {
        switchToTenant(previousTenant);
      }
    }

    @Test
    @DisplayName("Finding created in tenant X should NOT be readable from tenant Y")
    void given_findingInTenantX_should_notBeReadableFromTenantY() {
      // -------- Arrange --------
      String tenantX = TenantContext.getCurrentTenant();
      String tenantY = UUID.randomUUID().toString();

      Finding finding = createFindingInTenant(tenantX);
      switchToTenant(tenantY);

      // -------- Act & Assert --------
      assertThrows(EntityNotFoundException.class, () -> findingService.finding(finding.getId()));
    }

    @Test
    @DisplayName("Finding created in tenant X should be readable from tenant X")
    void given_findingInTenantX_should_beReadableFromTenantX() {
      // -------- Arrange --------
      String tenantX = TenantContext.getCurrentTenant();
      Finding finding = createFindingInTenant(tenantX);

      switchToTenant(tenantX);

      // -------- Act --------
      Finding result = findingService.finding(finding.getId());

      // -------- Assert --------
      assertEquals(finding.getId(), result.getId());
    }

    @Test
    @DisplayName("Finding list in tenant Y should NOT return findings from tenant X")
    void given_findingInTenantX_should_notAppearInTenantYList() {
      // -------- Arrange --------
      String tenantX = TenantContext.getCurrentTenant();
      String tenantY = UUID.randomUUID().toString();

      Finding findingInTenantX = createFindingInTenant(tenantX);
      switchToTenant(tenantY);

      // -------- Act --------
      List<Finding> findings = findingService.findings();
      Set<String> findingIds = findings.stream().map(Finding::getId).collect(Collectors.toSet());

      // -------- Assert --------
      assertFalse(findingIds.contains(findingInTenantX.getId()));
      assertTrue(findingIds.isEmpty());
    }
  }
}
