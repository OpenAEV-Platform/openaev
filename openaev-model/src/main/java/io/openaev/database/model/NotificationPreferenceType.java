package io.openaev.database.model;

/**
 * Notification types a user can choose the channels of (in-app, email) in the notification center's
 * preferences. Defaults: in-app on, email off.
 */
public enum NotificationPreferenceType {
  /** Payloads and new versions waiting for approval (sent to approvers). */
  PAYLOAD_APPROVAL_REQUESTS,
  /** Approval outcomes of the user's own payloads and versions (sent to authors). */
  PAYLOAD_APPROVAL_OUTCOMES
}
