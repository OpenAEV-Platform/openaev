package io.openaev;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUserTestExecutionListener;
import io.openaev.utilstest.RabbitMQTestListener;
import io.openaev.utilstest.StartupSnapshotTestListener;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@AutoConfigureMockMvc(print = MockMvcPrint.SYSTEM_ERR)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestExecutionListeners(
    value = {
      StartupSnapshotTestListener.class,
      WithMockUserTestExecutionListener.class,
      RabbitMQTestListener.class
    },
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class IntegrationTest {

  @Autowired GrantComposer grantComposer;
  @Autowired protected TestUserHolder testUserHolder;
  @Autowired protected EntityManager entityManager;
  @Autowired protected TenantRepository tenantRepository;
  @Autowired protected TenantMembershipCacheManager tenantMembershipCacheManager;

  public void addGrantToCurrentUser(
      Grant.GRANT_RESOURCE_TYPE grantResourceType, Grant.GRANT_TYPE grantType, String resourceId) {
    User user = testUserHolder.get();
    Group group = user.getGroups().stream().findAny().get();

    Grant grant = GrantFixture.getGrant(resourceId, grantResourceType, grantType, group);
    grantComposer.forGrant(grant).persist();

    // ensure changes are flushed and a fresh entity is seen
    entityManager.flush();
    entityManager.clear();

    // Refresh SecurityContext to reflect new authority
    testUserHolder.refreshSecurityContext();
  }

  protected String tenantUri(String uriTemplate) {
    User user = testUserHolder.get();
    String tenantId =
        user.getGroups().stream()
            .map(Group::getTenant)
            .filter(java.util.Objects::nonNull)
            .map(Tenant::getId)
            .findFirst()
            .orElse(TenantContext.getCurrentTenant());

    // Keep test user/tenant membership in sync with TenantInterceptor checks.
    tenantRepository.addUserToTenant(user.getId(), tenantId);
    tenantMembershipCacheManager.evict(user.getId(), tenantId);

    return uriTemplate.replace("{tenantId}", tenantId);
  }
}
