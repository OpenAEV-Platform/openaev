package io.openaev.config;

import java.util.Set;

/**
 * The derived marking scope dimension (Task 4, Option 2 "hide parents"): restricts a table whose
 * rows <i>hold</i> assets — an atomic testing, a finding — to the rows that hold no asset outside
 * the clearance in {@code app.current_markings}.
 *
 * <p>It sits next to {@link MarkingDimension} rather than inside it. {@link MarkingDimension} finds
 * its tables from the schema (a {@code marking_ids} column) and tests that column; a derived table
 * has no such column and its predicate is a call to a SQL function that follows the row's links
 * down to {@code assets.marking_ids}. Kept apart, the two compose for free: if a derived table
 * later gets markings of its own, both dimensions cover it and the inspector ANDs their predicates
 * — "I hold the row's own markings" and "I can see every asset it holds".
 *
 * <p>Reads and writes use the same predicate: an UPDATE or DELETE on a hidden row from a user
 * transaction affects nothing, consistent with the 404 on read. No write attribution: nothing is
 * stored, so there is no written value to validate.
 */
public final class DerivedMarkingDimension implements ScopeDimension {

  private final DerivedMarkedTables tables;

  public DerivedMarkingDimension(DerivedMarkedTables tables) {
    this.tables = tables;
  }

  @Override
  public String name() {
    return "derived-marking";
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
    return tables.get(table).predicate(alias);
  }

  @Override
  public String writePredicate(String table, String alias) {
    return readPredicate(table, alias);
  }
}
