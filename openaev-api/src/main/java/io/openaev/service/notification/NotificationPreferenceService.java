package io.openaev.service.notification;

import io.openaev.api.notification_preference.NotificationPreferenceInput;
import io.openaev.api.notification_preference.NotificationPreferenceOutput;
import io.openaev.database.model.NotificationPreferenceType;
import io.openaev.database.model.User;
import io.openaev.database.model.UserNotificationPreference;
import io.openaev.database.repository.UserNotificationPreferenceRepository;
import io.openaev.service.UserService;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The current user's notification preferences (global, not per tenant). */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

  private final UserNotificationPreferenceRepository preferenceRepository;
  private final UserService userService;

  /** Every type, with the stored channels or the defaults. */
  public List<NotificationPreferenceOutput> myPreferences() {
    return preferencesOf(userService.currentUser().getId());
  }

  /** Sets the channels of the given types for the current user. */
  public List<NotificationPreferenceOutput> updateMyPreferences(
      @NotNull final List<NotificationPreferenceInput> input) {
    User user = userService.currentUser();
    for (NotificationPreferenceInput change : input) {
      UserNotificationPreference preference =
          preferenceRepository
              .findByUserIdAndType(user.getId(), change.type())
              .orElseGet(
                  () -> {
                    UserNotificationPreference created = new UserNotificationPreference();
                    created.setUser(user);
                    created.setType(change.type());
                    return created;
                  });
      preference.setInApp(change.inApp());
      preference.setEmail(change.email());
      preferenceRepository.save(preference);
    }
    return preferencesOf(user.getId());
  }

  private List<NotificationPreferenceOutput> preferencesOf(String userId) {
    Map<NotificationPreferenceType, UserNotificationPreference> stored =
        preferenceRepository.findByUserId(userId).stream()
            .collect(Collectors.toMap(UserNotificationPreference::getType, Function.identity()));
    return Arrays.stream(NotificationPreferenceType.values())
        .map(
            type -> {
              UserNotificationPreference preference = stored.get(type);
              return new NotificationPreferenceOutput(
                  type,
                  preference != null
                      ? preference.isInApp()
                      : UserNotificationPreference.DEFAULT_IN_APP,
                  preference != null
                      ? preference.isEmail()
                      : UserNotificationPreference.DEFAULT_EMAIL);
            })
        .toList();
  }
}
