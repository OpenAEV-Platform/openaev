package io.openaev.service.user_events;

import static io.openaev.database.model.UserEventType.LOGIN_SUCCESS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.User;
import io.openaev.database.model.UserEvent;
import io.openaev.database.model.UserEventType;
import io.openaev.database.repository.UserEventRepository;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserEventService {

  private static final int DEFAULT_WINDOW_DAYS = 7;

  @Resource private ObjectMapper mapper;
  private final UserEventRepository userEventRepository;

  // -- CRUD --

  /** Creates a {@link UserEventType#LOGIN_SUCCESS} event for the given user. */
  @Async
  @Transactional
  public CompletableFuture<Void> createLoginSuccessEvent(User user) {
    this.createEvent(LOGIN_SUCCESS, user);
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Creates a {@link UserEventType#LOGIN_FAILED} event without an associated user.
   *
   * <p>This is typically used for authentication failures where the user identity is unknown or
   * cannot be resolved (e.g. OAuth2 / SAML failures).
   */
  @Async
  @Transactional
  public CompletableFuture<Void> createLoginFailedEvent(String provider, String reason) {
    JsonNode payload = mapper.createObjectNode().put("provider", provider).put("reason", reason);

    this.createEvent(UserEventType.LOGIN_FAILED, payload);
    return CompletableFuture.completedFuture(null);
  }

  private void createEvent(UserEventType type, User user) {
    Objects.requireNonNull(type, "event type must not be null");
    Objects.requireNonNull(user, "user must not be null");

    UserEvent event = new UserEvent();
    event.setType(type);
    event.setUser(user);
    this.userEventRepository.save(event);
  }

  private void createEvent(UserEventType type, JsonNode payload) {
    Objects.requireNonNull(type, "event type must not be null");
    Objects.requireNonNull(payload, "payload must not be null");

    UserEvent event = new UserEvent();
    event.setType(type);
    event.setPayload(payload);
    this.userEventRepository.save(event);
  }

  // -- METRICS --

  /** Computes the average number of successful logins per day over the given time window. */
  public long averageDailySuccessLogins(int windowDays) {
    int effectiveWindow = sanitizeWindowDays(windowDays);
    long totalLogins = countEventSuccessLogins(effectiveWindow);
    return totalLogins / effectiveWindow;
  }

  private long countEventSuccessLogins(int windowDays) {
    int effectiveWindow = sanitizeWindowDays(windowDays);
    Instant from = Instant.now().minus(Duration.ofDays(effectiveWindow));

    return this.userEventRepository.countEvents(LOGIN_SUCCESS, from);
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
