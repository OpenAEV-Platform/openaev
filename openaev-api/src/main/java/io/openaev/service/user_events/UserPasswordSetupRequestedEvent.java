package io.openaev.service.user_events;

/** Event published when a newly created user must initialize their password via reset flow. */
public record UserPasswordSetupRequestedEvent(String email) {}
