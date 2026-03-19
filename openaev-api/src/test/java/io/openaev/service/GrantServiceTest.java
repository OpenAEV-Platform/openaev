package io.openaev.service;

import static io.openaev.database.model.Grant.GRANT_RESOURCE_TYPE.SIMULATION;
import static io.openaev.database.model.Grant.GRANT_TYPE.PLANNER;
import static io.openaev.utils.fixtures.GrantFixture.getGrant;
import static io.openaev.utils.fixtures.GroupFixture.createGroup;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.database.repository.GrantRepository;
import io.openaev.utils.fixtures.UserFixture;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantServiceTest {

  private static final String USER_ID = "userid";
  private static final String RESOURCE_ID = "resourceid";

  @Mock private GrantRepository grantRepository;

  @InjectMocks private GrantService grantService;

  @Captor private ArgumentCaptor<Grant> grantCaptor;

  @Test
  void test_hasReadGrant_WHEN_has_read_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.OBSERVER.andHigher()))
        .thenReturn(true);

    assertTrue(grantService.hasReadGrant(RESOURCE_ID, user));
  }

  @Test
  void test_hasReadGrant_WHEN_has_no_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.OBSERVER.andHigher()))
        .thenReturn(false);

    assertFalse(grantService.hasReadGrant(RESOURCE_ID, user));
  }

  @Test
  void test_hasLaunchGrant_WHEN_has_read_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.LAUNCHER.andHigher()))
        .thenReturn(false);

    assertFalse(grantService.hasLaunchGrant(RESOURCE_ID, user));
  }

  @Test
  void test_hasWriteGrant_WHEN_has_read_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, PLANNER.andHigher()))
        .thenReturn(false);

    assertFalse(grantService.hasWriteGrant(RESOURCE_ID, user));
  }

  @Test
  void test_hasLaunchGrant_WHEN_has_launch_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.LAUNCHER.andHigher()))
        .thenReturn(true);

    assertTrue(grantService.hasLaunchGrant(RESOURCE_ID, user));
  }

  @Test
  void test_hasWriteGrant_WHEN_has_write_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, PLANNER.andHigher()))
        .thenReturn(true);

    assertTrue(grantService.hasWriteGrant(RESOURCE_ID, user));
  }

  @Test
  void given_validParameters_should_createGrantSuccessfully() {
    // -- ARRANGE --
    Group group = createGroup();

    // -- ACT --
    grantService.createGrant(PLANNER, group, RESOURCE_ID, SIMULATION);

    // -- ASSERT --
    verify(grantRepository).save(grantCaptor.capture());
    Grant captured = grantCaptor.getValue();
    assertEquals(PLANNER, captured.getName());
    assertEquals(group, captured.getGroup());
    assertEquals(RESOURCE_ID, captured.getResourceId());
    assertEquals(SIMULATION, captured.getGrantResourceType());
  }

  @Test
  void given_multipleSourceGrants_should_duplicateAllGrantsWithNewResourceId() {
    // -- ARRANGE --
    String targetResourceId = "new-resource-id";
    Group group = createGroup();
    Grant sourceGrant1 = getGrant(RESOURCE_ID, SIMULATION, Grant.GRANT_TYPE.OBSERVER, group);
    Grant sourceGrant2 = getGrant(RESOURCE_ID, SIMULATION, PLANNER, group);
    when(grantRepository.save(any(Grant.class))).thenAnswer(inv -> inv.getArgument(0));

    // -- ACT --
    List<Grant> result =
        grantService.duplicateGrants(
            List.of(sourceGrant1, sourceGrant2), targetResourceId, SIMULATION);

    // -- ASSERT --
    assertEquals(2, result.size());
    result.forEach(
        grant -> {
          assertEquals(targetResourceId, grant.getResourceId());
          assertEquals(group, grant.getGroup());
          assertEquals(SIMULATION, grant.getGrantResourceType());
        });
    assertEquals(Grant.GRANT_TYPE.OBSERVER, result.get(0).getName());
    assertEquals(PLANNER, result.get(1).getName());
  }
}
