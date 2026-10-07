package io.openaev.config;

import java.util.List;
import java.util.Locale;

/**
 * A table whose visibility is <i>derived</i> from the markings of the assets its rows hold, rather
 * than carried by a {@code marking_ids} column of its own (see {@link MarkedTable} for that case).
 *
 * <p>The predicate is a template over the table alias, built from SQL functions created by a
 * migration ({@code can_see_inject}, {@code can_see_finding}, ...). Those functions are listed so
 * the configuration can refuse to start when one is missing, instead of letting Postgres fail on
 * the first rewritten query.
 *
 * @param table the derived table, e.g. {@code injects}
 * @param predicateTemplate the read predicate, with {@code %1$s} standing for the table alias
 * @param functions the SQL functions the predicate calls
 */
public record DerivedMarkedTable(String table, String predicateTemplate, List<String> functions) {

  public DerivedMarkedTable {
    table = table.toLowerCase(Locale.ROOT);
    functions = List.copyOf(functions);
  }

  /** The predicate for one reference to this table, parenthesized so it ANDs safely. */
  public String predicate(String alias) {
    return "(" + predicateTemplate.formatted(alias) + ")";
  }
}
