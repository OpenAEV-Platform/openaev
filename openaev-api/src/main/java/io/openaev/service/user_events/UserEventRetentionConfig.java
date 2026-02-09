package io.openaev.service.user_events;

import static io.openaev.database.model.SettingKeys.*;

import io.openaev.database.model.UserEventType;
import io.openaev.service.settings.SettingService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventRetentionConfig {

  private final SettingService settingService;

  public boolean isEnabled() {
    return settingService.getBoolean(USER_EVENTS_RETENTION_ENABLED);
  }

  public void setEnabled(boolean value) {
    settingService.setBoolean(USER_EVENTS_RETENTION_ENABLED, value);
  }

  public int getDefaultDays() {
    return settingService.getInt(USER_EVENTS_RETENTION_DEFAULT_DAYS);
  }

  public int getRetentionDays(UserEventType type) {
    Objects.requireNonNull(type, "type must not be null");

    return switch (type) {
      case LOGIN_SUCCESS -> settingService.getInt(USER_EVENTS_RETENTION_LOGIN_SUCCESS_DAYS);
      case LOGIN_FAILED -> settingService.getInt(USER_EVENTS_RETENTION_LOGIN_FAILED_DAYS);
      case USER_CREATED -> settingService.getInt(USER_EVENTS_RETENTION_USER_CREATED_DAYS);
      default -> getDefaultDays();
    };
  }
}
