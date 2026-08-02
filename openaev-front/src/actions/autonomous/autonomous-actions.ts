import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { WorkflowConfigurationInput } from '../../utils/api-types';
import type {
  AutonomousAttackPathStepState,
  AutonomousDirective,
  AutonomousEvent,
  AutonomousObjectiveTemplate,
  AutonomousRun,
  AutonomousRunCreateInput,
  CapabilityQueryInput,
  CapabilityReport,
} from './autonomous-types';

// Autonomous (AI-driven) attack-path run client, gated by the AUTONOMOUS_ATTACK_PATH preview
// feature. The tenant prefix is added centrally by Action.buildUri, so these use the plain /api
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

// -- run lifecycle --

export const createAutonomousRun = (
  input: AutonomousRunCreateInput,
): Promise<{ data: AutonomousRun }> => simplePostCall(AUTONOMOUS_URI, input);

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

export const startAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/start`);

export const pauseAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/pause`);

export const resumeAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/resume`);

export const cancelAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/cancel`);

// Restart a terminal run in place: reuses the same scenario, tears the old simulation down and
// provisions a fresh one, resetting the run to CREATED. Pair with startAutonomousRun to re-engage
// the orchestrator - no new scenario is created.
export const restartAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/restart`);

// Promote a completed dry-run plan to a real, executing run in place: the backend tears the
// non-executing plan simulation + mirrored plan steps down, provisions a fresh executing
// simulation, clears plan mode and keeps the plan summary as guidance, resetting the run to
// CREATED. Pair with startAutonomousRun to engage the orchestrator on the live run.
export const promoteAutonomousRun = (runId: string): Promise<{ data: AutonomousRun }> =>
  simplePostCall(`${AUTONOMOUS_URI}/${runId}/promote`);

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
