package io.openaev.service.user_events;

import static io.openaev.database.model.UserEventType.LOGIN;

import io.openaev.database.model.User;
import io.openaev.database.model.UserEvent;
import io.openaev.database.model.UserEventType;
import io.openaev.database.repository.UserEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserEventService {

  private static final int DEFAULT_WINDOW_DAYS = 7;

  private final UserEventRepository userEventRepository;

  // -- CRUD --

  public void createLoginEvent(User user) {
    this.createEvent(user, LOGIN);
  }

  private void createEvent(User user, UserEventType type) {
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(type, "event type must not be null");

    UserEvent event = new UserEvent();
    event.setUser(user);
    event.setType(type);
    this.userEventRepository.save(event);
  }

  // -- METRICS --

  public long averageDailyLogins(int windowDays) {
    int effectiveWindow = sanitizeWindowDays(windowDays);
    long totalLogins = countEventLogins(effectiveWindow);
    return totalLogins / effectiveWindow;
  }

  private long countEventLogins(int windowDays) {
    int effectiveWindow = sanitizeWindowDays(windowDays);
    Instant from = Instant.now().minus(Duration.ofDays(effectiveWindow));

    return this.userEventRepository.countEvents(LOGIN, from);
  }

  // -- UTILS --

  private int sanitizeWindowDays(int windowDays) {
    if (windowDays <= 0) {
      log.warn("Invalid windowDays={}, fallback to {}", windowDays, DEFAULT_WINDOW_DAYS);
      return DEFAULT_WINDOW_DAYS;
    }
    return windowDays;
  }
}
