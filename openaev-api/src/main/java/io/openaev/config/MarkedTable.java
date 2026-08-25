package io.openaev.config;

import java.util.Locale;

/**
 * How one marked table reaches its markings. A marking is a many-to-many association, so unlike the
 * tenant scope there is no local column to test: the predicate has to reach the join table, which
 * needs the four names below.
 *
 * @param table the marked table, e.g. {@code assets}
 * @param pkColumn its primary key column, e.g. {@code asset_id}
 * @param joinTable the association table, e.g. {@code assets_markings}
 * @param joinFkColumn the column of the join table pointing back at {@link #table}, e.g. {@code
 *     asset_id}
 */
public record MarkedTable(String table, String pkColumn, String joinTable, String joinFkColumn) {

  /** The column of every join table holding the marking side of the association. */
  public static final String MARKING_COLUMN = "marking_id";

  /** Suffix of the join table of a marked table — the convention schema derivation relies on. */
  public static final String JOIN_TABLE_SUFFIX = "_markings";

  public MarkedTable {
    table = table.toLowerCase(Locale.ROOT);
    pkColumn = pkColumn.toLowerCase(Locale.ROOT);
    joinTable = joinTable.toLowerCase(Locale.ROOT);
    joinFkColumn = joinFkColumn.toLowerCase(Locale.ROOT);
  }
}
