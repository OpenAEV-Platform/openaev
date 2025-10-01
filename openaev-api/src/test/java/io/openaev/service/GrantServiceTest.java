package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Grant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GrantRepository;
import io.openaev.utils.fixtures.UserFixture;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GrantServiceTest extends IntegrationTest {

  private static final String USER_ID = "userid";
  private static final String RESOURCE_ID = "resourceid";

  @Mock private GrantRepository grantRepository;

  @InjectMocks private GrantService grantService;

  @Test
  public void test_hasReadGrant_WHEN_has_read_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.OBSERVER.andHigher()))
        .thenReturn(true);

    assertTrue(grantService.hasReadGrant(RESOURCE_ID, user));
  }

  @Test
  public void test_hasReadGrant_WHEN_has_no_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.OBSERVER.andHigher()))
        .thenReturn(false);

    assertFalse(grantService.hasReadGrant(RESOURCE_ID, user));
  }

  @Test
  public void test_hasLaunchGrant_WHEN_has_read_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.LAUNCHER.andHigher()))
        .thenReturn(false);

    assertFalse(grantService.hasLaunchGrant(RESOURCE_ID, user));
  }

  @Test
  public void test_hasWriteGrant_WHEN_has_read_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.PLANNER.andHigher()))
        .thenReturn(false);

    assertFalse(grantService.hasWriteGrant(RESOURCE_ID, user));
  }

  @Test
  public void test_hasLaunchGrant_WHEN_has_launch_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.LAUNCHER.andHigher()))
        .thenReturn(true);

    assertTrue(grantService.hasLaunchGrant(RESOURCE_ID, user));
  }

  @Test
  public void test_hasWriteGrant_WHEN_has_write_grant() {
    User user = UserFixture.getUser();
    user.setId(USER_ID);
    when(grantRepository.existsByUserIdAndResourceIdAndNameIn(
            USER_ID, RESOURCE_ID, Grant.GRANT_TYPE.PLANNER.andHigher()))
        .thenReturn(true);

    assertTrue(grantService.hasWriteGrant(RESOURCE_ID, user));
  }
}
