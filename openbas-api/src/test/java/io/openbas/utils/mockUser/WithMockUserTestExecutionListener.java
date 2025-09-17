package io.openbas.utils.mockUser;

import static io.openbas.service.UserService.buildAuthenticationToken;

import io.openbas.database.model.User;
import io.openbas.utils.fixtures.GroupFixture;
import io.openbas.utils.fixtures.RoleFixture;
import io.openbas.utils.fixtures.UserFixture;
import io.openbas.utils.fixtures.composers.GrantComposer;
import io.openbas.utils.fixtures.composers.GroupComposer;
import io.openbas.utils.fixtures.composers.RoleComposer;
import io.openbas.utils.fixtures.composers.UserComposer;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class WithMockUserTestExecutionListener extends AbstractTestExecutionListener {

  @Override
  public void beforeTestExecution(TestContext testContext) throws Exception {
    WithMockUser annotation = findWithMockUserAnnotation(testContext);
    if (annotation == null) {
      return; // no mock user configured
    }

    var ctx = testContext.getApplicationContext();
    UserComposer userComposer = ctx.getBean(UserComposer.class);
    GroupComposer groupComposer = ctx.getBean(GroupComposer.class);
    RoleComposer roleComposer = ctx.getBean(RoleComposer.class);
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
    User testUser =
        userComposer
            .forUser(
                UserFixture.getUser(userFirstName, userLastName, userMail, annotation.isAdmin()))
            .withGroup(
                groupComposer
                    .forGroup(GroupFixture.createGroup())
                    .withRole(
                        roleComposer.forRole(
                            RoleFixture.getRole(Set.of(annotation.withCapabilities())))))
            .persist()
            .get();
    userComposer.reset(); // reset to avoid side effects in following tests

    testUserHolder.set(testUser);

    Authentication authentication = buildAuthenticationToken(testUser);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Override
  public void afterTestMethod(TestContext testContext) {
    var ctx = testContext.getApplicationContext();
    UserComposer userComposer = ctx.getBean(UserComposer.class);
    GroupComposer groupComposer = ctx.getBean(GroupComposer.class);
    RoleComposer roleComposer = ctx.getBean(RoleComposer.class);
    GrantComposer grantComposer = ctx.getBean(GrantComposer.class);
    TestUserHolder testUserHolder = ctx.getBean(TestUserHolder.class);

    testUserHolder.clear();
    grantComposer.reset();
    roleComposer.reset();
    groupComposer.reset();
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
