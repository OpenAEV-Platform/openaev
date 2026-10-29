package io.openaev.config;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How one marked table is marked. Three shapes:
 *
 * <ul>
 *   <li><b>Own column</b> — the marking is a {@code text[]} kept inline on the row. A marking is a
 *       many-to-many association, but storing the set inline means the predicate needs nothing but
 *       the column name, and the marked table's primary key is irrelevant. That is what lets
 *       relationship tables, whose primary keys are composite, be marked with no special case.
 *   <li><b>Through a parent</b> ({@link #parent()}) — the table carries no marking of its own and
 *       is marked by the one row it points to through a foreign key (e.g. {@code
 *       agents.agent_asset} pointing to {@code assets}). Nothing is copied, so nothing can go
 *       stale: the parent row is the single source of truth, and changing its markings changes what
 *       every linked row shows.
 *   <li><b>Through link rows</b> ({@link #linkRows()}) — the table is pointed to by a link table
 *       holding one row per association (e.g. {@code findings} by {@code findings_assets}), so it
 *       has no single parent to follow. It is shown when it has no link, or when at least one of
 *       its links is itself visible.
 * </ul>
 *
 * @param table the marked table, e.g. {@code assets}
 * @param markingColumn the column holding its marking ids, by convention {@link #MARKING_COLUMN};
 *     {@code null} unless the table has its own column
 * @param parent the parent this table is marked through, or {@code null}
 * @param linkRows the link rows this table is marked through, or {@code null}
 */
public record MarkedTable(
    String table, String markingColumn, ParentLink parent, LinkRows linkRows) {

  /** The column holding the marking ids of a marked row — the convention derivation relies on. */
  public static final String MARKING_COLUMN = "marking_ids";

  /**
   * Identifiers end up verbatim in generated SQL, so they are restricted to plain names. They come
   * from code, not from users, but a typo must fail at startup rather than at query time.
   */
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  /**
   * The foreign key through which a table is marked by a parent row.
   *
   * @param foreignKeyColumn the column of the marked table that references the parent
   * @param parentTable the parent table; it must itself be marked (own column or linked)
   * @param parentKeyColumn the parent's column the foreign key references
   */
  public record ParentLink(String foreignKeyColumn, String parentTable, String parentKeyColumn) {

    public ParentLink {
      foreignKeyColumn = identifier(foreignKeyColumn);
      parentTable = identifier(parentTable);
      parentKeyColumn = identifier(parentKeyColumn);
    }
  }

  /**
   * The link rows through which a table is marked: one row per association, each of which is marked
   * itself (it points to a marked parent in turn).
   *
   * @param keyColumn the column of the marked table that the link rows reference
   * @param linkTable the link table; it must itself be marked (own column or linked)
   * @param linkForeignKeyColumn the column of the link table that references the marked table
   */
  public record LinkRows(String keyColumn, String linkTable, String linkForeignKeyColumn) {

    public LinkRows {
      keyColumn = identifier(keyColumn);
      linkTable = identifier(linkTable);
      linkForeignKeyColumn = identifier(linkForeignKeyColumn);
    }
  }

  /** A column that must exist for a link to be usable, as a (table, column) pair. */
  public record ColumnRef(String table, String column) {}

  public MarkedTable(String table) {
    this(table, MARKING_COLUMN, null, null);
  }

  public MarkedTable(String table, String markingColumn) {
    this(table, markingColumn, null, null);
  }

  public MarkedTable(String table, String markingColumn, ParentLink parent) {
    this(table, markingColumn, parent, null);
  }

  public MarkedTable {
    table = table.toLowerCase(Locale.ROOT);
    int shapes =
        (markingColumn != null ? 1 : 0) + (parent != null ? 1 : 0) + (linkRows != null ? 1 : 0);
    if (shapes != 1) {
      throw new IllegalArgumentException(
          table + " must be marked in exactly one way: its own column, a parent, or link rows");
    }
    if (markingColumn != null) {
      markingColumn = markingColumn.toLowerCase(Locale.ROOT);
    }
  }

  /** A table with no marking of its own, marked by the parent row its foreign key points to. */
  public static MarkedTable linkedTo(
      String table, String foreignKeyColumn, String parentTable, String parentKeyColumn) {
    return new MarkedTable(
        identifier(table),
        null,
        new ParentLink(foreignKeyColumn, parentTable, parentKeyColumn),
        null);
  }

  /** A table with no marking of its own, marked by the link rows that point to it. */
  public static MarkedTable throughLinkRows(
      String table, String keyColumn, String linkTable, String linkForeignKeyColumn) {
    return new MarkedTable(
        identifier(table), null, null, new LinkRows(keyColumn, linkTable, linkForeignKeyColumn));
  }

  /** Whether the table is marked through another table rather than by a column of its own. */
  public boolean isLinked() {
    return parent != null || linkRows != null;
  }

  /** The table this one is marked through, or {@code null} when it has its own column. */
  public String linkedTable() {
    if (parent != null) {
      return parent.parentTable();
    }
    return linkRows != null ? linkRows.linkTable() : null;
  }

  /** The columns a link names, so they can be checked against the schema at startup. */
  public List<ColumnRef> linkedColumns() {
    if (parent != null) {
      return List.of(
          new ColumnRef(table, parent.foreignKeyColumn()),
          new ColumnRef(parent.parentTable(), parent.parentKeyColumn()));
    }
    if (linkRows != null) {
      return List.of(
          new ColumnRef(table, linkRows.keyColumn()),
          new ColumnRef(linkRows.linkTable(), linkRows.linkForeignKeyColumn()));
    }
    return List.of();
  }

  private static String identifier(String name) {
    if (name == null || !IDENTIFIER.matcher(name).matches()) {
      throw new IllegalArgumentException("not a plain SQL identifier: " + name);
    }
    return name.toLowerCase(Locale.ROOT);
  }
}
