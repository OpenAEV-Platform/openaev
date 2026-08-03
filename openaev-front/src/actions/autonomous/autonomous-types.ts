// Local types for the autonomous (AI-driven) attack-path feature, mirroring the backend DTOs in
// io.openaev.api.autonomous.dto and io.openaev.database.model.autonomous. These live here (rather
// than in the generated api-types.d.ts) until `yarn generate-types-from-api` is run against a built
// backend carrying the new endpoints; once regenerated, switch the imports to api-types.

import { type WorkflowScopeRuleInput } from '../../utils/api-types';

export type AutonomousRunStatus
  = | 'CREATED'
    | 'PLANNING'
    | 'PLANNED'
    | 'RUNNING'
    | 'PAUSED'
    | 'WAITING_INPUT'
    | 'COMPLETED'
    | 'FAILED'
    | 'CANCELED';

// Mirrors io.openaev.database.model.autonomous.AutonomousEventType.
export type AutonomousEventType
  = | 'NARRATION'
    | 'DECISION'
    | 'TOOL_ACTION'
    | 'HANDOVER'
    | 'GAP'
    | 'STATUS'
    | 'DIRECTIVE'
    | 'QUESTION'
    | 'PROOF';

export type AutonomousDirectiveStatus = 'PENDING' | 'CONSUMED';

export interface AutonomousRun {
  autonomous_run_id: string;
  autonomous_run_objective: string;
  autonomous_run_objective_template_key?: string | null;
  autonomous_run_scenario_id?: string | null;
  autonomous_run_simulation_id?: string | null;
  autonomous_run_scope_asset_group_id?: string | null;
  autonomous_run_scope_team_id?: string | null;
  autonomous_run_scope?: AutonomousScopeTarget[] | null;
  autonomous_run_status: AutonomousRunStatus;
  // Dry-run flag: the orchestrator is only designing the attack path; nothing is executed and the
  // cockpit is styled in draft orange until the operator runs it for real.
  autonomous_run_plan_mode?: boolean;
  autonomous_run_plan_guidance?: string | null;
  autonomous_run_xtm_session_id?: string | null;
  autonomous_run_xtm_agent_slug?: string | null;
  autonomous_run_last_error?: string | null;
  autonomous_run_created_at?: string;
  autonomous_run_updated_at?: string;
}

export interface AutonomousEvent {
  autonomous_event_id: string;
  autonomous_event_run_id: string;
  autonomous_event_sequence: number;
  autonomous_event_type: AutonomousEventType;
  autonomous_event_title?: string | null;
  autonomous_event_content?: string | null;
  autonomous_event_data?: string | null;
  autonomous_event_created_at?: string;
}

// Mirrors io.openaev.api.autonomous.dto.AutonomousAttackPathStepState: one step already authored on
// the run's attack path (its backing simulation inject, live status and traces). The hero counts
// these for the autonomous inject/step stat so it matches the Execution tab exactly.
export interface AutonomousAttackPathStepState {
  inject_id?: string;
  title?: string | null;
  type?: string | null;
  injector_contract_id?: string | null;
  status?: string | null;
  traces?: string[] | null;
}

export interface AutonomousDirective {
  autonomous_directive_id: string;
  autonomous_directive_run_id: string;
  autonomous_directive_content: string;
  autonomous_directive_status: AutonomousDirectiveStatus;
  autonomous_directive_created_at?: string;
  autonomous_directive_consumed_at?: string | null;
}

export interface AutonomousObjectiveTemplate {
  autonomous_objective_template_id: string;
  autonomous_objective_template_key: string;
  autonomous_objective_template_label: string;
  autonomous_objective_template_description?: string | null;
  autonomous_objective_template_icon?: string | null;
  autonomous_objective_template_prompt: string;
  autonomous_objective_template_kill_chain_focus?: string | null;
  // 'environment' = whole authorized scope, no target choice needed; 'target' = needs a specific
  // target the operator picks up front (optional scope selector) or the orchestrator asks for.
  autonomous_objective_template_scope_mode?: string | null;
  autonomous_objective_template_builtin?: boolean;
}

export interface AutonomousRunCreateInput {
  objective?: string;
  objective_template_key?: string;
  name?: string;
  description?: string;
  // Advanced/optional: seed from an existing chaining scenario. Left empty for a fully
  // autonomous run, where the attack-path substrate is auto-provisioned server-side.
  scenario_id?: string;
  scope_asset_group_id?: string;
  scope_team_id?: string;
  scope?: AutonomousScopeTarget[];
  // Full scope definition (allow-list + deny-list, every source incl. manual IP / CIDR / hostname /
  // CSV) seeded onto the run's scenario and simulation workflows, matching the manual chained-scope
  // editor. Superset of `scope`. Empty means "skip scope at launch; the AI will resolve it".
  scope_rules?: WorkflowScopeRuleInput[];
  agent_slug?: string;
  // Dry-run: design the attack path (scope, steps, decisions) without executing anything. The
  // operator can review the plan and later run it for real.
  plan_mode?: boolean;
}

// One entry of an autonomous run's mixed scope. `type` uses the platform target-kind vocabulary;
// `id` is the entity id of that kind. `name` is a client-side convenience label for the chip UI.
export type AutonomousScopeTargetType = 'ASSETS' | 'ASSETS_GROUPS' | 'TEAMS';

export interface AutonomousScopeTarget {
  type: AutonomousScopeTargetType;
  id: string;
  name?: string;
}

// -- Capability resolution --

export type CapabilityKind = 'TECHNIQUE' | 'OUTPUT_TYPE' | 'KILL_CHAIN_PHASE';

export interface CapabilityQueryInput {
  techniques?: string[];
  output_types?: string[];
  platforms?: string[];
  objective_template_key?: string;
}

export interface ResolvedContract {
  injector_contract_id: string;
  label?: string;
  injector_type?: string;
  platforms?: string[];
}

export interface SuggestedConnector {
  connector_id: string;
  title?: string;
  slug?: string;
  short_description?: string;
  logo_url?: string;
  subscription_link?: string;
  source_code?: string;
}

export interface CapabilityResolution {
  kind: CapabilityKind;
  token: string;
  label?: string;
  satisfied: boolean;
  contracts?: ResolvedContract[];
  suggested_connectors?: SuggestedConnector[];
}

export interface CapabilityReport {
  resolutions: CapabilityResolution[];
  gaps: CapabilityResolution[];
  fully_satisfied: boolean;
}
