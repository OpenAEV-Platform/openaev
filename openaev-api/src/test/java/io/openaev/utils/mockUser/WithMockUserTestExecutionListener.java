package io.openaev.utils.mockUser;

import static io.openaev.service.UserService.buildAuthenticationToken;

import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityScope;
import io.openaev.database.model.User;
import io.openaev.utils.fixtures.PlatformRoleFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class WithMockUserTestExecutionListener extends AbstractTestExecutionListener {

  /**
   * Run after TransactionalTestExecutionListener (order 4000) so the transaction is already active,
   * but before @BeforeEach so that test setup methods have access to the mock user and tenant
   * context.
   */
  @Override
  public int getOrder() {
    return 5000;
  }

  @Override
  public void beforeTestMethod(TestContext testContext) throws Exception {
    WithMockUser annotation = findWithMockUserAnnotation(testContext);
    if (annotation == null) {
      return; // no mock user configured
    }

    var ctx = testContext.getApplicationContext();
    EntityManager entityManager = ctx.getBean(EntityManager.class);
    UserComposer userComposer = ctx.getBean(UserComposer.class);
    TenantGroupComposer tenantGroupComposer = ctx.getBean(TenantGroupComposer.class);
    TenantRoleComposer tenantRoleComposer = ctx.getBean(TenantRoleComposer.class);
    TestUserHolder testUserHolder = ctx.getBean(TestUserHolder.class);

    // Build user from annotation
    String userFirstName =
        annotation.userFirstName().isEmpty()
            ? UUID.randomUUID().toString()
            : annotation.userFirstName();
    String userLastName =
        annotation.userLastName().isEmpty()
            ? UUID.randomUUID().toString()
            : annotation.userLastName();
    String userMail =
        annotation.userMail().isEmpty()
            ? UUID.randomUUID() + "@unittests.invalid"
            : annotation.userMail();
    // Mirror production scoping: a platform-only capability cannot live in a tenant role
    // (Capability.validateForTenantRole rejects it), so it must be carried by a platform group.
    // Anything tenant-scoped stays in the tenant group, BYPASS included.
    Set<Capability> annotated = Set.of(annotation.withCapabilities());
    Set<Capability> platformOnly =
        annotated.stream()
            .filter(capability -> !capability.getScopes().contains(CapabilityScope.TENANT))
            .collect(Collectors.toSet());
    Set<Capability> tenantScoped =
        annotated.stream()
            .filter(capability -> !platformOnly.contains(capability))
            .collect(Collectors.toSet());

    UserComposer.Composer composer =
        userComposer
            .forUser(
                UserFixture.getUser(userFirstName, userLastName, userMail, annotation.isAdmin()))
            .withGroup(
                tenantGroupComposer
                    .forGroup(TenantGroupFixture.getGroup())
                    .withRole(tenantRoleComposer.forRole(TenantRoleFixture.getRole(tenantScoped))));

    if (!platformOnly.isEmpty()) {
      PlatformGroupComposer platformGroupComposer = ctx.getBean(PlatformGroupComposer.class);
      PlatformRoleComposer platformRoleComposer = ctx.getBean(PlatformRoleComposer.class);
      composer.withGroup(
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("mock-" + UUID.randomUUID()))
              .withRole(
                  platformRoleComposer.forPlatformRole(
                      PlatformRoleFixture.getPlatformRole(
                          "mock-" + UUID.randomUUID(), platformOnly))));
    }

    User testUser = composer.persist().get();
    userComposer.reset(); // reset to avoid side effects in following tests

    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      entityManager.flush();
      entityManager.clear();
    }

    testUserHolder.set(testUser);

    Authentication authentication = buildAuthenticationToken(testUser);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Override
  public void afterTestMethod(TestContext testContext) {
    var ctx = testContext.getApplicationContext();
    UserComposer userComposer = ctx.getBean(UserComposer.class);
    TenantGroupComposer tenantGroupComposer = ctx.getBean(TenantGroupComposer.class);
    TenantRoleComposer tenantRoleComposer = ctx.getBean(TenantRoleComposer.class);
    PlatformGroupComposer platformGroupComposer = ctx.getBean(PlatformGroupComposer.class);
    PlatformRoleComposer platformRoleComposer = ctx.getBean(PlatformRoleComposer.class);
    GrantComposer grantComposer = ctx.getBean(GrantComposer.class);
    TestUserHolder testUserHolder = ctx.getBean(TestUserHolder.class);

    // Composers are singletons tracking what they generated; the rows themselves go away with the
    // test transaction. The platform pair is reset too since the mock user may carry
    // platform-scoped capabilities, which only a platform group can hold.
    testUserHolder.clear();
    grantComposer.reset();
    tenantRoleComposer.reset();
    tenantGroupComposer.reset();
    platformRoleComposer.reset();
    platformGroupComposer.reset();
    userComposer.reset();
  }

  private WithMockUser findWithMockUserAnnotation(TestContext testContext) {
    // 1. Check method first
    WithMockUser annotation =
        AnnotatedElementUtils.findMergedAnnotation(testContext.getTestMethod(), WithMockUser.class);
    if (annotation != null) {
      return annotation;
    }

    // 2. Check class and enclosing classes recursively
    Class<?> clazz = testContext.getTestClass();
    while (clazz != null) {
      annotation = AnnotatedElementUtils.findMergedAnnotation(clazz, WithMockUser.class);
      if (annotation != null) {
        return annotation;
      }
      clazz = clazz.getEnclosingClass(); // walk up nested hierarchy
    }

    return null; // none found
  }
}
