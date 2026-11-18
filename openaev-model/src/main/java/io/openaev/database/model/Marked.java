package io.openaev.database.model;

/** Entity whose row carries its own markings, in a nullable {@code marking_ids text[]} column. */
public interface Marked {

  String[] getMarkingIds();
}
