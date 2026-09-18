package io.openaev.service.user_events;

import io.openaev.database.model.User;

/** Event published when a user has requested changing their account email. */
public record UserEmailChangeRequestedEvent(User user, String newEmail) {}
