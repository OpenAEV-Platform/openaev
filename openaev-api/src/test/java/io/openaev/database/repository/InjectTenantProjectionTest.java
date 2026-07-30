package io.openaev.database.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * #6357: the batched inject-execution callback consumer ({@code
 * BatchingInjectStatusService.handleInjectExecutionCallback}) groups callbacks by their inject's
 * tenant on a worker thread that carries no ambient tenant (or the wrong one), so the resolution
 * must not depend on the Hibernate tenant {@code @Filter}. This pins that {@code
 * findTenantIdByInjectId} is native and therefore filter-exempt: under one tenant's active scope it
 * still returns another tenant's inject's real owning tenant, where a filtered query would be
 * empty.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("inject tenant projection is native and filter-exempt (#6357)")
class InjectTenantProjectionTest extends IntegrationTest {

  @Autowired private InjectRepository injectRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("resolves each inject's owning tenant under a different active scope")
  void resolvesOwningTenantFilterExempt() throws Exception {
    Tenant tenantA = tenantHelper.createTenantWithCurrentUser("proj-a");
    Tenant tenantB = tenantHelper.createTenantWithCurrentUser("proj-b");

    String injectA = seedInjectInTenant(tenantA.getId());
    String injectB = seedInjectInTenant(tenantB.getId());

    // Ambient scope = tenant B, with its @Filter enabled: a filtered read of tenant A's inject
    // would
    // be empty. The native projection is filter-exempt, so it still resolves each inject's real
    // owning tenant.
    tenantHelper.switchToTenant(tenantB.getId(), entityManager);

    Map<String, String> tenantByInjectId =
        injectRepository
            .findTenantIdsByInjectIds(List.of(injectA, injectB, "does-not-exist"))
            .stream()
            .collect(Collectors.toMap(row -> (String) row[0], row -> (String) row[1]));

    assertThat(tenantByInjectId).containsEntry(injectA, tenantA.getId());
    assertThat(tenantByInjectId).containsEntry(injectB, tenantB.getId());
    // An unknown inject is simply absent, so the consumer falls back to the default tenant.
    assertThat(tenantByInjectId).doesNotContainKey("does-not-exist");
  }

  private String seedInjectInTenant(String tenantId) {
    tenantHelper.switchToTenant(tenantId, entityManager);
    InjectorContract contract = injectorContractFixture.getWellKnownSingleEmailContract();
    InjectComposer.Composer injectC =
        injectComposer.forInject(InjectFixture.createInject(contract, "callback-tenant-probe"));
    exerciseComposer
        .forExercise(ExerciseFixture.createDefaultExercise())
        .withInjects(List.of(injectC))
        .persist();
    return injectC.get().getId();
  }
}
