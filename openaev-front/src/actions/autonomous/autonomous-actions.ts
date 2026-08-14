import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { Scenario, WorkflowConfigurationInput } from '../../utils/api-types';
import type {
  AdditionalAgent,
  AutonomousAttackPathStepState,
  AutonomousDefaultAgents,
  AutonomousDirective,
  AutonomousEvent,
  AutonomousObjectiveTemplate,
  AutonomousRun,
  AutonomousRunCreateInput,
  CapabilityQueryInput,
  CapabilityReport,
} from './autonomous-types';

// Autonomous (AI-driven) attack-path run client. Autonomy is a launch mode of chained scenarios.
// The tenant
// prefix is added centrally by Action.buildUri, so these use the plain /api
// paths. Reads pass defaultNotifyErrorBehavior=false so polling failures don't spam toasts; the
// mutating calls keep the default error toast.
const AUTONOMOUS_URI = '/api/autonomous-runs';

// -- objective templates + capability resolution (run-creation gallery + gap strip) --

export const fetchObjectiveTemplates = (): Promise<{ data: AutonomousObjectiveTemplate[] }> =>
  simpleCall(`${AUTONOMOUS_URI}/objective-templates`, undefined, false);

export const resolveCapabilities = (
  input: CapabilityQueryInput,
): Promise<{ data: CapabilityReport }> =>
  simplePostCall(`${AUTONOMOUS_URI}/capabilities/resolve`, input, undefined, false);

// -- additional (specialist) agents the orchestrator can consult --

// Specialist agents sourced from XTM One's aev.attack_path_additional_agent intent catalog. Returns
// an empty list (never a toast) when XTM One is not configured or exposes no such agents, so the UI
// can degrade to a CTA-only state.
export const fetchAvailableAgents = (): Promise<{ data: AdditionalAgent[] }> =>
  simpleCall(`${AUTONOMOUS_URI}/available-agents`, undefined, false);

// The tenant's default additional agents (enabled ids + each agent's default discovery mode)
// attached to every new autonomous run.
export const fetchDefaultAgents = (): Promise<{ data: AutonomousDefaultAgents }> =>
  simpleCall(`${AUTONOMOUS_URI}/default-agents`, undefined, false);

export const updateDefaultAgents = (
  agentIds: string[],
  agentModes: Record<string, string>,
): Promise<{ data: AutonomousDefaultAgents }> =>
  simplePutCall(`${AUTONOMOUS_URI}/default-agents`, {
    agent_ids: agentIds,
    agent_modes: agentModes,
  });

// -- run lifecycle --

export const createAutonomousRun = (
  input: AutonomousRunCreateInput,
): Promise<{ data: AutonomousRun }> => simplePostCall(AUTONOMOUS_URI, input);

// Launch an existing chained scenario in AUTONOMOUS mode: the backend seeds a live simulation from
// the scenario's authored attack path and engages the orchestrator to verify, execute, and
// adapt/extend it. Creates AND starts the run in one call; returns the run (with its simulation id).
export const launchAutonomousFromScenario = (
  scenarioId: string,
  input?: Partial<AutonomousRunCreateInput>,
): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/from-scenario/${scenarioId}`, input ?? {});

// Plan an existing chained scenario with the orchestrator (author-scenario mode): the orchestrator
// designs the attack path by writing steps directly onto the scenario workflow. No simulation is
// provisioned and nothing is executed. Creates AND starts the (dry-run) planning session.
export const planAutonomousScenario = (
  scenarioId: string,
  input?: Partial<AutonomousRunCreateInput>,
): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/plan-scenario/${scenarioId}`, input ?? {});

export const fetchAutonomousRuns = (): Promise<{ data: AutonomousRun[] }> =>
  simpleCall(AUTONOMOUS_URI, undefined, false);

export const fetchAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simpleCall(`${AUTONOMOUS_URI}/${runId}`, undefined, false);

// Detect whether a simulation is AI-driven: returns the run when autonomous, 404 (rejected promise)
// otherwise. Reads pass defaultNotifyErrorBehavior=false so the expected 404/403 on manual or non-EE
// simulations never raises a toast.
export const fetchAutonomousRunBySimulation = (
  simulationId: string,
): Promise<{ data: AutonomousRun }> =>
  simpleCall(`${AUTONOMOUS_URI}/by-simulation/${simulationId}`, undefined, false);

// Scenario-side twin of the above: an autonomous run owns exactly one scenario, so this detects an
// AI-driven scenario. 404 (rejected promise) on a manual scenario; toast suppressed.
export const fetchAutonomousRunByScenario = (
  scenarioId: string,
): Promise<{ data: AutonomousRun }> =>
  simpleCall(`${AUTONOMOUS_URI}/by-scenario/${scenarioId}`, undefined, false);

// Read the autonomous-run configuration saved on a chained scenario (the AI builder's "Save for
// later"), so the builder drawer can pre-fill. `data` is null when nothing was saved. Toast
// suppressed - a scenario with no saved config is expected, not an error.
export const fetchScenarioAutonomousConfig = (
  scenarioId: string,
): Promise<{ data: AutonomousRunCreateInput | null }> =>
  simpleCall(`${AUTONOMOUS_URI}/scenario-config/${scenarioId}`, undefined, false);

// Persist an autonomous-run configuration on a chained scenario WITHOUT starting a run. The
// scenario stays a normal, editable chained scenario; the config is only used to pre-fill the
// builder and seed a later Build (plan) or Launch.
export const saveScenarioAutonomousConfig = (
  scenarioId: string,
  input: AutonomousRunCreateInput,
): Promise<{ data: AutonomousRunCreateInput | null }> =>
  simplePutCall(`${AUTONOMOUS_URI}/scenario-config/${scenarioId}`, input);

export const startAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/start`);

export const pauseAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/pause`);

export const resumeAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/resume`);

export const cancelAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/cancel`);

// Turn an autonomous scenario into a manual chained scenario. DUPLICATE clones it into a brand-new
// manual scenario and leaves the AI run untouched; IN_PLACE flips this scenario to manual for good
// (halts the run, drops the autonomous_runs row + timeline, unlocks the simulation for edit/delete)
// and is irreversible. Returns the resulting manual chained scenario to navigate to.
export const convertAutonomousRunToManual = (
  runId: string,
  mode: 'DUPLICATE' | 'IN_PLACE',
): Promise<{ data: Scenario }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/convert-to-manual`, { mode });

// -- live view: decision timeline + steering --

// The decision timeline, optionally since a sequence cursor for incremental polling. Pairs with the
// attack-path graph delta over the shared /api/stream SSE nudge (event attack-path-version).
export const fetchAutonomousTimeline = (
  runId: string,
  since = 0,
): Promise<{ data: AutonomousEvent[] }> =>
  simpleCall(`${AUTONOMOUS_URI}/${runId}/timeline?since=${since}`, undefined, false);

export const fetchAutonomousDirectives = (
  runId: string,
): Promise<{ data: AutonomousDirective[] }> =>
  simpleCall(`${AUTONOMOUS_URI}/${runId}/directives`, undefined, false);

// Live snapshot of every step already authored on the run's attack path (each backing simulation
// inject, its status and traces). The hero counts these so the autonomous inject/step stat matches
// the Execution tab exactly, since an autonomous run authors its injects on the simulation rather
// than on the scenario.
export const fetchAutonomousAttackPathState = (
  runId: string,
): Promise<{ data: AutonomousAttackPathStepState[] }> =>
  simpleCall(`${AUTONOMOUS_URI}/${runId}/attack-path/state`, undefined, false);

// Queue a real-time steering directive; consumed by the orchestrator on its next decision cycle, so
// it steers the live run without stopping it.
export const addAutonomousDirective = (
  runId: string,
  content: string,
): Promise<{ data: AutonomousDirective }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/directives`, { content });

// Apply a live scope / rate-limit / safe-mode edit to the run's RUN workflow(s) without stopping it.
export const updateAutonomousConfiguration = (
  runId: string,
  input: WorkflowConfigurationInput,
): Promise<{ data: unknown }> =>
  simplePutCall(`${AUTONOMOUS_URI}/${runId}/configuration`, input);
