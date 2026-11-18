package io.openaev.config;

import static io.openaev.config.MarkedTable.linkedTo;
import static io.openaev.config.MarkedTable.throughLinkRows;

import java.util.List;
import java.util.stream.Stream;

/**
 * The tables <i>derived</i> from a marked table: they carry no marking of their own, and are hidden
 * whenever the marked row they come from is hidden. Nothing is copied: the marked row stays the
 * single source of truth, so re-marking an asset changes, instantly, every row derived from it.
 *
 * <p>This is part of the data model, not of the deployment, hence code rather than configuration.
 * Activation follows the marked table: listing {@code assets} in {@code
 * openaev.marking.active-tables} filters every table derived from it, and a derived table whose
 * marked table is not active stays unfiltered (see {@link MarkedTables#withDerived}). A derived
 * table is never listed in {@code active-tables}, which only names tables that carry a {@code
 * marking_ids} column.
 *
 * <p>Each entry spells out the columns of one hop, because the predicate is generated SQL and must
 * name them; a chain (a table derived from a derived table) resolves hop by hop down to the marked
 * table. The columns are checked against the schema by {@code MarkingLinkedTablesRowsTest}.
 */
public final class MarkingDerivedTables {

  /**
   * The links from a parent to an asset. Hibernate rewrites a changed collection with {@code DELETE
   * ... WHERE <parent>_id = ?} and re-inserts what it loaded; derived, that DELETE only reaches the
   * links the user can see, so editing the targets of an inject or the members of a static group
   * never drops an asset the user cannot see. It also hides the asset id from the reads that skip
   * the join to {@code assets} (ids, counts).
   */
  public static final List<MarkedTable> LINKS_TO_ASSETS =
      List.of(
          linkedTo("injects_assets", "asset_id", "assets", "asset_id"),
          linkedTo("asset_groups_assets", "asset_id", "assets", "asset_id"));

  /**
   * The objects a run produces on an asset: what an execution on a restricted asset leaves behind
   * must not exist for a reader without its clearance.
   */
  public static final List<MarkedTable> FROM_ASSETS =
      List.of(
          // A link between a finding and an asset is visible when the asset is, for the same
          // reasons as LINKS_TO_ASSETS.
          linkedTo("findings_assets", "asset_id", "assets", "asset_id"),
          // A finding is visible when it has no asset, or at least one visible asset.
          throughLinkRows("findings", "finding_id", "findings_assets", "finding_id"),
          // Asset and agent expectations both carry the asset; team, player and asset group
          // expectations have no asset_id, so they stay visible.
          linkedTo("injects_expectations", "asset_id", "assets", "asset_id"),
          // An agent belongs to its asset, and a trace (the agent's output) to its agent.
          // Traces with no agent are the inject's global traces and stay visible.
          linkedTo("agents", "agent_asset", "assets", "asset_id"),
          linkedTo("execution_traces", "execution_agent_id", "agents", "agent_id"));

  /** Every derived table, in no particular order: {@link MarkedTables} resolves the chains. */
  public static final List<MarkedTable> ALL =
      Stream.concat(LINKS_TO_ASSETS.stream(), FROM_ASSETS.stream()).toList();

  private MarkingDerivedTables() {}
}
