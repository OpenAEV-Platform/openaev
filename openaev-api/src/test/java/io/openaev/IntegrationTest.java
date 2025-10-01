package io.openaev;

import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUserTestExecutionListener;
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
    value = {StartupSnapshotTestListener.class, WithMockUserTestExecutionListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class IntegrationTest {

  @Autowired GrantComposer grantComposer;
  @Autowired protected TestUserHolder testUserHolder;
  @Autowired protected EntityManager entityManager;

  public void addGrantToCurrentUser(
      Grant.GRANT_RESOURCE_TYPE grantResourceType, Grant.GRANT_TYPE grantType, String resourceId) {
    User user = testUserHolder.get();
    Group group = user.getGroups().getFirst();

    Grant grant = GrantFixture.getGrant(resourceId, grantResourceType, grantType, group);
    grantComposer.forGrant(grant).persist();

    // ensure changes are flushed and a fresh entity is seen
    entityManager.flush();
    entityManager.clear();

    // Refresh SecurityContext to reflect new authority
    testUserHolder.refreshSecurityContext();
  }
}
