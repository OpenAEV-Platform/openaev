package io.openaev.config;

import java.util.Set;

/**
 * The marking scope dimension: restricts every active marked table to the rows whose markings are
 * all covered by the clearance in {@code app.current_markings}, through the {@code
 * is_marking_set_allowed} SQL function.
 *
 * <p>Markings are many-to-many, but the set is stored inline on the row as a {@code text[]}, so the
 * predicate is a local column test of the same shape as the tenant one:
 *
 * <pre>{@code
 * is_marking_set_allowed(t.marking_ids)
 * }</pre>
 *
 * read as "keep this row if I hold every marking it carries". Three consequences follow, and they
 * are the intended semantics: a row must satisfy <b>every</b> one of its markings (AND, the STIX
 * reading), a row with no marking is visible to everyone for free (the empty set is contained in
 * everything), and a marking can only ever reduce visibility.
 *
 * <p>Because the markings live in a column rather than a join table, the marked table's primary key
 * never appears in the predicate — which is what lets relationship tables, whose keys are
 * composite, be marked with no special case.
 *
 * <p>Reads and writes use the same predicate: seeing a row and being allowed to touch it are the
 * same question here. Restricting which <i>markings</i> may be written is a service-layer concern,
 * not something this rewrite can express — and under this shape it is also what keeps a nonexistent
 * marking id out of the column, since no foreign key does.
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
    return "is_marking_set_allowed(" + alias + "." + marked.markingColumn() + ")";
  }

  @Override
  public String writePredicate(String table, String alias) {
    return readPredicate(table, alias);
  }
}
