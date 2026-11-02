package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.annotation.ControlledUuidGeneration;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Channels a user receives one notification type on. Global to the user (not per tenant), like the
 * user's language or theme. No row for a type means the defaults: in-app on, email off.
 */
@Getter
@Setter
@Entity
@Table(name = "user_notification_preferences")
public class UserNotificationPreference implements Base {

  public static final boolean DEFAULT_IN_APP = true;
  public static final boolean DEFAULT_EMAIL = false;

  @Id
  @ControlledUuidGeneration
  @Column(name = "user_notification_preference_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", updatable = false, nullable = false)
  @JsonIgnore
  private User user;

  @Column(name = "user_notification_preference_type", nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private NotificationPreferenceType type;

  @Column(name = "user_notification_preference_in_app", nullable = false)
  private boolean inApp = DEFAULT_IN_APP;

  @Column(name = "user_notification_preference_email", nullable = false)
  private boolean email = DEFAULT_EMAIL;

  @Override
  public boolean isListened() {
    return false;
  }
}
