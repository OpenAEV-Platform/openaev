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
 * <p>A table can instead be marked <i>through a parent</i> (see {@link MarkedTable#parent()}): it
 * has no column of its own and is hidden whenever the parent row it points to is. That one shape
 * does put the foreign key and the parent's key in the predicate, in exchange for there being no
 * copy of the marking to keep in sync.
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
    return predicate(table, alias, 0);
  }

  /**
   * The predicate of one table. A table with its own marking column is a local test. A table marked
   * through a parent asks whether the parent row it points to is allowed, recursively, so a chain
   * (job → agent → asset) resolves hop by hop to the one column that holds the marking:
   *
   * <pre>{@code
   * (t.agent_asset IS NULL OR EXISTS (
   *    SELECT 1 FROM assets mkp0_assets
   *    WHERE mkp0_assets.asset_id = t.agent_asset
   *      AND is_marking_set_allowed(mkp0_assets.marking_ids)))
   * }</pre>
   *
   * <p>Two choices there are the semantics, not detail:
   *
   * <ul>
   *   <li>A NULL foreign key means "points to no asset", hence no marking, hence visible — the same
   *       reading as an empty marking set. Without the {@code IS NULL} branch, rows that merely
   *       have no asset (a team expectation, say) would vanish.
   *   <li>A parent the reader cannot see hides the row. That is the point of linking: what is
   *       attached to a restricted asset must not exist for someone without its clearance.
   * </ul>
   *
   * <p>The sub-select reads the parent table directly rather than going through the inspector again
   * (the inspector collects the selects of the original statement only), so the parent's own
   * predicate is spelled out here. The parent's tenant is not re-checked: the foreign key already
   * ties the row to a parent of its own tenant, and the child is tenant-filtered on its own where
   * it is a tenant table.
   *
   * <p>{@code depth} keeps the generated aliases distinct across the levels of a chain, and the
   * {@code mkp} prefix keeps them clear of any alias the surrounding query uses.
   */
  private String predicate(String table, String alias, int depth) {
    MarkedTable marked = tables.get(table);
    if (marked.linkRows() != null) {
      return linkRowsPredicate(marked.linkRows(), alias, depth);
    }
    MarkedTable.ParentLink parent = marked.parent();
    if (parent == null) {
      return "is_marking_set_allowed(" + alias + "." + marked.markingColumn() + ")";
    }
    String parentAlias = "mkp" + depth + "_" + parent.parentTable();
    String fk = alias + "." + parent.foreignKeyColumn();
    return "("
        + fk
        + " IS NULL OR EXISTS (SELECT 1 FROM "
        + parent.parentTable()
        + " "
        + parentAlias
        + " WHERE "
        + parentAlias
        + "."
        + parent.parentKeyColumn()
        + " = "
        + fk
        + " AND "
        + predicate(parent.parentTable(), parentAlias, depth + 1)
        + "))";
  }

  /**
   * The predicate of a table marked through link rows (a finding and its {@code findings_assets}):
   *
   * <pre>{@code
   * (NOT EXISTS (SELECT 1 FROM findings_assets mkl0_findings_assets
   *              WHERE mkl0_findings_assets.finding_id = t.finding_id)
   *  OR EXISTS (SELECT 1 FROM findings_assets mkl0_findings_assets
   *             WHERE mkl0_findings_assets.finding_id = t.finding_id
   *               AND <the link table's own predicate on mkl0_findings_assets>))
   * }</pre>
   *
   * <p>Read as "no link, or at least one link I may see". That is a deliberately <b>permissive</b>
   * reading, the opposite of the AND over markings used on a single row, and it is a product
   * decision, not a technicality: a finding attached to a visible and to a restricted asset stays
   * visible, because it is derived from an asset the reader may see. The cost is that such a
   * finding may carry information that came from the restricted one. The strict alternative (hide
   * it when any link is hidden) only needs the second branch replaced by {@code NOT EXISTS (... AND
   * NOT <link predicate>)}.
   *
   * <p>A table with no link row at all is visible, like an unmarked row. Two sibling sub-selects
   * can reuse one alias since their scopes do not overlap; the {@code mkl} prefix keeps it clear of
   * the surrounding query, and {@code depth} of the levels of a chain.
   */
  private String linkRowsPredicate(MarkedTable.LinkRows links, String alias, int depth) {
    String linkAlias = "mkl" + depth + "_" + links.linkTable();
    String join =
        " FROM "
            + links.linkTable()
            + " "
            + linkAlias
            + " WHERE "
            + linkAlias
            + "."
            + links.linkForeignKeyColumn()
            + " = "
            + alias
            + "."
            + links.keyColumn();
    return "(NOT EXISTS (SELECT 1"
        + join
        + ") OR EXISTS (SELECT 1"
        + join
        + " AND "
        + predicate(links.linkTable(), linkAlias, depth + 1)
        + "))";
  }

  @Override
  public String writePredicate(String table, String alias) {
    return readPredicate(table, alias);
  }
}
