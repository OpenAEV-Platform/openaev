package io.openaev.api.notification_preference;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.NotificationPreferenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** The channels the current user receives one notification type on. */
public record NotificationPreferenceOutput(
    @Schema(description = "Notification type")
        @JsonProperty("notification_preference_type")
        @NotNull
        NotificationPreferenceType type,
    @Schema(description = "Shown in the notification center and the bell")
        @JsonProperty("notification_preference_in_app")
        boolean inApp,
    @Schema(description = "Also sent by email") @JsonProperty("notification_preference_email")
        boolean email) {}
