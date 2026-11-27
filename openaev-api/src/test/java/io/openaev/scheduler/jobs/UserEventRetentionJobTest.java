package io.openaev.scheduler.jobs;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Setting;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.model.User;
import io.openaev.database.model.UserEvent;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.UserEventRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.scheduler.jobs.user_event.UserEventRetentionJob;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserEventRetentionJobTest extends IntegrationTest {

  @Autowired private EntityManager entityManager;

  @Autowired private UserEventRepository userEventRepository;

  @Autowired private UserEventRetentionJob job;

  @Autowired private UserRepository userRepository;

  @Autowired private SettingRepository settingRepository;

  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  @Test
  void should_delete_old_login_events_and_keep_recent_ones() throws JobExecutionException {
    // -- ARRANGE --
    User user = userRepository.save(getUser());
    insertUserEvent(user);
    // sanity check
    assertThat(userEventRepository.count()).isEqualTo(2);

    // -- ACT --
    job.execute(null);

    // -- ASSERT --
    List<UserEvent> remaining = fromIterable(userEventRepository.findAll());
    assertThat(remaining).hasSize(1);
  }

  @Test
  void should_do_nothing_when_disabled() throws JobExecutionException {
    // -- ARRANGE --
    User user = userRepository.save(getUser());
    insertUserEvent(user);
    // sanity check
    assertThat(userEventRepository.count()).isEqualTo(2);
    disableRetention();

    // -- ACT --
    job.execute(null);

    // -- ASSERT --
    List<UserEvent> remaining = fromIterable(userEventRepository.findAll());
    assertThat(remaining).hasSize(2);
  }

  // -- PRIVATE --

  /** Writes the platform row that switches the retention off, as a platform-write transaction. */
  void disableRetention() {
    tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
    String key = SettingKeys.USER_EVENTS_RETENTION_ENABLED.key();
    Setting setting =
        settingRepository.findByKeyAndTenantIsNull(key).orElseGet(() -> new Setting(key, "false"));
    setting.setValue("false");
    settingRepository.save(setting);
    entityManager.flush();
  }

  /**
   * Create a user event using a native query to bypass the automatic createdAt value and manually
   * control it.
   */
  void insertUserEvent(User user) {
    entityManager
        .createNativeQuery(
            """
                                  INSERT INTO user_events (
                                    user_event_id,
                                    user_id,
                                    user_event_type,
                                    user_event_created_at
                                  ) VALUES (
                                    gen_random_uuid(),
                                    :userId,
                                    'LOGIN_SUCCESS',
                                    now() - interval '100 days'
                                  )
                                """)
        .setParameter("userId", user.getId())
        .executeUpdate();

    entityManager
        .createNativeQuery(
            """
                                  INSERT INTO user_events (
                                    user_event_id,
                                    user_id,
                                    user_event_type,
                                    user_event_created_at
                                  ) VALUES (
                                    gen_random_uuid(),
                                    :userId,
                                    'LOGIN_SUCCESS',
                                    now() - interval '5 days'
                                  )
                                """)
        .setParameter("userId", user.getId())
        .executeUpdate();

    entityManager.flush();
  }
}
