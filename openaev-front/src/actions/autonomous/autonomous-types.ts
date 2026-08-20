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
    | 'AGENT_DELEGATION'
    | 'GAP'
    | 'STATUS'
    | 'DIRECTIVE'
    | 'QUESTION'
    | 'PROOF';

export type AutonomousDirectiveStatus = 'PENDING' | 'CONSUMED';

// Mirrors io.openaev.database.model.autonomous.AutonomousDiscoveryMode: how much latitude an agent
// has to bring newly discovered entities (assets, findings, persons) into the attack path.
export type AutonomousDiscoveryMode = 'EXISTING_ONLY' | 'SCOPED' | 'EXPANSIVE';
export const AUTONOMOUS_DISCOVERY_MODES: AutonomousDiscoveryMode[] = ['EXISTING_ONLY', 'SCOPED', 'EXPANSIVE'];
export const DEFAULT_DISCOVERY_MODE: AutonomousDiscoveryMode = 'SCOPED';
// Role-based defaults: the orchestrator stays inside the operator-defined scope (and asks the
// operator to widen it) while consulted specialist / additional agents are recon-oriented and by
// default expand the perimeter to bring in newly discovered entities. Mirrors
// AutonomousDiscoveryMode.ORCHESTRATOR_DEFAULT / SPECIALIST_DEFAULT on the backend.
export const ORCHESTRATOR_DEFAULT_DISCOVERY_MODE: AutonomousDiscoveryMode = 'SCOPED';
export const SPECIALIST_DEFAULT_DISCOVERY_MODE: AutonomousDiscoveryMode = 'EXPANSIVE';

// Stable sentinel key for the orchestrator itself in the per-agent discovery-mode map. The
// orchestrator's concrete XTM One id is only resolved at engage time (via the aev.attack_path
// orchestrator intent), so the UI and OpenAEV key its mode under this reserved id instead. OpenAEV
// enforcement falls back to this mode for any creation not attributed to a known specialist (i.e.
// the orchestrator acting on its own). Must match ORCHESTRATOR_AGENT_ID in AutonomousRunService.
export const ORCHESTRATOR_AGENT_ID = '__orchestrator__';

export interface AutonomousRun {
  autonomous_run_id: string;
  autonomous_run_objective: string;
  autonomous_run_objective_template_key?: string | null;
  autonomous_run_scenario_id?: string | null;
  autonomous_run_simulation_id?: string | null;
  autonomous_run_scope_asset_group_id?: string | null;
  autonomous_run_scope_team_id?: string | null;
  autonomous_run_scope?: AutonomousScopeTarget[] | null;
  autonomous_run_agent_ids?: string[] | null;
  autonomous_run_agent_modes?: Record<string, string> | null;
  autonomous_run_status: AutonomousRunStatus;
  // Dry-run flag: the orchestrator is only designing the attack path; nothing is executed and the
  // cockpit is styled in draft orange until the operator runs it for real.
  autonomous_run_plan_mode?: boolean;
  autonomous_run_plan_guidance?: string | null;
  autonomous_run_xtm_session_id?: string | null;
  autonomous_run_xtm_agent_slug?: string | null;
  autonomous_run_last_error?: string | null;
  // OpenAEV-owned run timeout: max lifetime in seconds and the absolute instant the watchdog
  // hard-stops the run (null in plan/dry-run mode).
  autonomous_run_timeout_seconds?: number | null;
  autonomous_run_started_at?: string | null;
  autonomous_run_deadline_at?: string | null;
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
  // XTM One agent ids the orchestrator may consult as specialist handover targets during the run
  // (in addition to the built-in payload creator). Empty -> the tenant's configured defaults.
  agent_ids?: string[];
  // Per-agent discovery mode (agent id -> EXISTING_ONLY / SCOPED / EXPANSIVE) for this run. When
  // omitted, the tenant's configured default per-agent modes are used.
  agent_modes?: Record<string, string>;
  // Dry-run: design the attack path (scope, steps, decisions) without executing anything. The
  // operator can review the plan and later run it for real.
  plan_mode?: boolean;
  // Refine (follow-up) build: the orchestrator refines the scenario's EXISTING authored logic
  // instead of rebuilding from scratch. The authored steps + a prior AI-built run's decision
  // timeline (history) are kept and reopened. false/omitted = rebuild from scratch (wipe + fresh).
  // Only meaningful for the plan/build action.
  refine?: boolean;
  // OpenAEV-enforced maximum run lifetime in seconds. Defaults to 24h server-side when omitted.
  timeout_seconds?: number;
}

// Mirrors io.openaev.api.autonomous.dto.AutonomousDefaultAgentsOutput: the tenant's default agent
// selection plus each agent's default discovery mode.
export interface AutonomousDefaultAgents {
  agent_ids: string[];
  agent_modes: Record<string, string>;
}

// A specialist agent the orchestrator can consult, from XTM One's aev.attack_path_additional_agent
// intent catalog. Mirrors io.openaev.api.xtmone.dto.ChatbotAgentOutput.
export interface AdditionalAgent {
  id: string;
  name?: string | null;
  slug?: string | null;
  description?: string | null;
}

// One entry of an autonomous run's mixed scope. `type` uses the platform target-kind vocabulary;
// `id` is the entity id of that kind. `name` is a client-side convenience label for the chip UI.
export type AutonomousScopeTargetType = 'ASSETS' | 'ASSETS_GROUPS' | 'TEAMS' | 'PLAYERS';

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
