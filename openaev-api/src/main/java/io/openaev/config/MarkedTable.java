package io.openaev.config;

import java.util.Locale;

/**
 * How one marked table stores its markings. A marking is a many-to-many association, but the set is
 * kept inline on the row as a {@code text[]}, so — unlike a join-table model — the predicate needs
 * nothing but the column name, and the marked table's primary key is irrelevant. That is what lets
 * relationship tables, whose primary keys are composite, be marked with no special case.
 *
 * @param table the marked table, e.g. {@code assets}
 * @param markingColumn the column holding its marking ids, by convention {@link #MARKING_COLUMN}
 */
public record MarkedTable(String table, String markingColumn) {

  /** The column holding the marking ids of a marked row — the convention derivation relies on. */
  public static final String MARKING_COLUMN = "marking_ids";

  public MarkedTable(String table) {
    this(table, MARKING_COLUMN);
  }

  public MarkedTable {
    table = table.toLowerCase(Locale.ROOT);
    markingColumn = markingColumn.toLowerCase(Locale.ROOT);
  }
}
