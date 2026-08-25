package io.openaev.config;

import java.util.Set;

/**
 * The marking scope dimension: restricts every active marked table to the rows whose markings are
 * all covered by the clearance in {@code app.current_markings}, through the {@code
 * can_access_marking} SQL function.
 *
 * <p>Because markings are many-to-many, the predicate is an <b>anti-join</b> rather than a column
 * test:
 *
 * <pre>{@code
 * NOT EXISTS (SELECT 1 FROM assets_markings t_mk
 *              WHERE t_mk.asset_id = t.asset_id
 *                AND is_marking_missing(t_mk.marking_id))
 * }</pre>
 *
 * read as "keep this row if it carries no marking I lack". Three consequences follow, and they are
 * the intended semantics: a row must satisfy <b>every</b> one of its markings (AND, the STIX
 * reading), a row with no marking is visible to everyone for free, and a marking can only ever
 * reduce visibility. The positive form ({@code EXISTS … AND <caller holds it>}) would be wrong
 * twice over: it would hide unmarked rows and leak rows carrying one allowed marking next to a
 * disallowed one — which is why the SQL function is named for the missing half rather than the
 * allowed one.
 *
 * <p>Reads and writes use the same predicate: seeing a row and being allowed to touch it are the
 * same question here. Restricting which <i>markings</i> may be written is a service-layer concern
 * (the marking of a row is set through the join table, which is not itself a marked table), not
 * something this rewrite can express.
 */
public final class MarkingDimension implements ScopeDimension {

  private final MarkedTables tables;

  public MarkingDimension(MarkedTables tables) {
    this.tables = tables;
  }

  @Override
  public String name() {
    return "marking";
  }

  @Override
  public Set<String> activeTables() {
    return tables.tableNames();
  }

  @Override
  public boolean covers(String table) {
    return tables.get(table) != null;
  }

  @Override
  public String readPredicate(String table, String alias) {
    MarkedTable marked = tables.get(table);
    // Derived from the alias so the correlation is unambiguous even when the same marked table is
    // joined twice in one statement.
    String joinAlias = alias + "_mk";
    return "NOT EXISTS (SELECT 1 FROM "
        + marked.joinTable()
        + " "
        + joinAlias
        + " WHERE "
        + joinAlias
        + "."
        + marked.joinFkColumn()
        + " = "
        + alias
        + "."
        + marked.pkColumn()
        + " AND is_marking_missing("
        + joinAlias
        + "."
        + MarkedTable.MARKING_COLUMN
        + "))";
  }

  @Override
  public String writePredicate(String table, String alias) {
    return readPredicate(table, alias);
  }
}
