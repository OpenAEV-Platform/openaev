package io.openaev.service.user_events;

/** Event published when a user must receive a password setup or reset email. */
public record UserPasswordSetupRequestedEvent(String email, String lang) {}
