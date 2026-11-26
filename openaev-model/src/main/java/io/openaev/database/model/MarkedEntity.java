package io.openaev.database.model;

/**
 * An entity whose table carries its own markings: the {@code marking_ids} column of a marked table.
 *
 * <p>{@link io.openaev.database.audit.ModelBaseListener} remembers the markings each row was loaded
 * or last written with, so the event of a write that changes them carries the previous set. The
 * stream then sends a reader who gains sight of the row a creation, and one who loses it a
 * deletion. Every entity of an active marked table with its own column implements it: {@code
 * StreamMarkingRules} refuses to start otherwise.
 */
public interface MarkedEntity extends Base {

  String[] getMarkingIds();

  /**
   * The markings the row was loaded or last written with: null for a row neither loaded nor written
   * in this session.
   */
  String[] getLoadedMarkingIds();

  void setLoadedMarkingIds(String[] markingIds);
}
