package io.openaev.service.scenario.revision;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSETS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TEAMS;

import java.util.Set;

/**
 * The client's configuration of a catalogue scenario: never written by an update or a rollback,
 * never in a revision. Everything else the catalogue manages is content (ADR-018, section 3).
 */
public final class ScenarioConfigFields {

  /** Inject: assets, groups, teams, all teams, enabled and tags are columns, never merged. */
  public static final Set<String> INJECT_COLUMNS =
      Set.of("assets", "asset_groups", "teams", "all_teams", "enabled", "tags");

  /** Keys of {@code inject_content} that hold targets: the selector and the manual hosts. */
  public static final Set<String> INJECT_CONTENT_KEYS =
      Set.of(
          "target_selector",
          "targets",
          CONTRACT_ELEMENT_CONTENT_KEY_ASSETS,
          CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS,
          CONTRACT_ELEMENT_CONTENT_KEY_TEAMS);

  /**
   * Scenario: teams and players, schedule and recurrence, mail sender and reply-to, tags. The
   * merged scenario block holds name, description, severity and category only.
   */
  public static final Set<String> SCENARIO_FIELDS =
      Set.of(
          "teams",
          "users",
          "recurrence",
          "recurrence_start",
          "recurrence_end",
          "mail_from",
          "reply_to",
          "tags");

  /** Workflow: step targets, scope rules and variables, rate limit, timeout, safe mode. */
  public static final Set<String> WORKFLOW_FIELDS =
      Set.of(
          "step_data",
          "scope_rules",
          "scope_variables",
          "rate_limit",
          "timeout",
          "safe_mode",
          "keep_alive");

  private ScenarioConfigFields() {}
}
