package io.openaev.database.model;

/**
 * Discriminates what kind of event a {@link FindingTriageHistory} row records. Added so the
 * append-only history (originally triage-only) can also carry archive/un-archive events, giving
 * product a single unified timeline for "what happened to this finding and who did it" instead of
 * two disconnected logs.
 *
 * <p>{@code TRIAGE_CHANGE} rows always have non-null {@code fromStatus}/{@code toStatus}; {@code
 * ARCHIVE}/{@code UNARCHIVE} rows have both null (there is no "triage status" transition to
 * describe for those).
 */
public enum FindingHistoryActionType {
  TRIAGE_CHANGE,
  ARCHIVE,
  UNARCHIVE
}
