package io.openaev.database.model;

public enum StableFindingLifecycle {
  ACTIVE,
  REVIEW_REQUIRED,
  MUTED,
  /** Closed by a later compliant (PASS) result on the same location; a new FAIL reopens it. */
  FIXED
}
