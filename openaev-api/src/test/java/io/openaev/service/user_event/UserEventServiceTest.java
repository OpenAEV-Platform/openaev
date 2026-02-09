package io.openaev.service.user_event;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static io.openaev.utils.fixtures.user_event.UserEventFixture.getUserEventLogin;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.User;
import io.openaev.database.model.UserEvent;
import io.openaev.database.model.UserEventType;
import io.openaev.database.repository.UserEventRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.user_events.UserEventService;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserEventServiceTest extends IntegrationTest {

  @Autowired private UserEventService userEventService;

  @Autowired private UserEventRepository userEventRepository;

  @Autowired private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    // async execution: transaction rollback not applied
    userEventRepository.deleteAll();
  }

  // -- CRUD --

  @Test
  void should_create_login_success_event() {
    // -- ARRANGE --
    User user = userRepository.save(getUser());

    // -- ACT --
    userEventService.createLoginSuccessEvent(user).join();

    // -- ASSERT --
    List<UserEvent> events = fromIterable(userEventRepository.findAll());

    assertThat(events)
        .hasSize(1)
        .allMatch(
            e ->
                e.getType() == UserEventType.LOGIN_SUCCESS
                    && e.getUser().getId().equals(user.getId()));
  }

  @Test
  void should_create_login_failed_event() {
    // -- ACT --
    userEventService.createLoginFailedEvent("local login", "BadCredentialsException").join();

    // -- ASSERT --
    List<UserEvent> events = fromIterable(userEventRepository.findAll());

    assertThat(events)
        .hasSize(1)
        .allMatch(e -> e.getType() == UserEventType.LOGIN_FAILED && e.getUser() == null);
  }

  @Test
  void should_create_user_created_event() {
    // -- ARRANGE --
    User user = userRepository.save(getUser());

    // -- ACT --
    userEventService.createUserCreatedEvent(user, "saml").join();

    // -- ASSERT --
    List<UserEvent> events = fromIterable(userEventRepository.findAll());

    assertThat(events)
        .hasSize(1)
        .allMatch(
            e ->
                e.getType() == UserEventType.USER_CREATED
                    && e.getUser().getId().equals(user.getId()));
  }

  // -- METRICS --

  @Test
  void should_compute_average_daily_logins() {
    // -- ARRANGE --
    User user = userRepository.save(getUser());
    List<UserEvent> userEvents =
        List.of(getUserEventLogin(user), getUserEventLogin(user), getUserEventLogin(user));
    userEventRepository.saveAll(userEvents);

    // -- ACT --
    assertThat(userEventRepository.count()).isEqualTo(3);

    // -- ASSERT --
    long avg = userEventService.averageDailySuccessLogins(3);
    assertThat(avg).isEqualTo(1);
  }
}
