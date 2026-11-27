package io.openaev.service.catalog.merge;

/** What a three-way merge does with a field or an element, for a payload or a scenario. */
public enum MergeStatus {
  TAKE_APPLIED,
  KEEP_MINE,
  CONFLICT,
  ADDED,
  REMOVED;

  public boolean keepsMine(MergeChoice choice) {
    return this == CONFLICT ? choice == MergeChoice.KEEP_MINE : this == KEEP_MINE;
  }
}
