import { simpleCall, simplePostCall } from '../../utils/Action';
import type { AttackPathDTO, AttackPathEndpointRelationsDTO, AttackPathExecutionDetailDTO, AttackPathExpandDTO, AttackPathFindingPageDTO, AttackPathSimSummaryRow, ExerciseSimple } from '../../utils/api-types';
import type { AttackPathDeltaDTO } from '../../utils/api-types-custom';

// Attack-path execution-store POC (issue 6647), gated by the ATTACK_PATH preview feature.
// The tenant prefix is added centrally by Action.buildUri, so these use the plain /api paths.
const ATTACK_PATH_URI = '/api/attack-path';

const simulationUri = (simulationId: string) => `${ATTACK_PATH_URI}/simulations/${simulationId}`;

// The simulations that have attack-path data in the caller's tenant (id + endpoint/execution counts),
// for the picker.
export const fetchAttackPathSimulations = (): Promise<{ data: AttackPathSimSummaryRow[] }> =>
  simpleCall(`${ATTACK_PATH_URI}/simulations`);

// Resolve real simulations' metadata (name + start date) by id, so the picker can show a readable
// date + name instead of the raw id. Uses the existing exercises endpoint (body is a GetExercisesInput
// = { exercise_ids }); synthetic seeded ids (ap-seed-*) simply return no match and fall back to the
// id front-side. Errors are handled by the caller (no toast). The graph reads keep using the raw
// simulationId — this is display only.
export const fetchSimulationsMetaById = (simulationIds: string[]): Promise<{ data: ExerciseSimple[] }> =>
  simplePostCall('/api/exercises/search-by-id', { exercise_ids: simulationIds }, undefined, false);

// Rebuild the whole graph. Without a mode the backend auto-selects full or collapsed on size;
// passing 'full' or 'collapsed' forces it.
export const fetchAttackPathGraph = (
  simulationId: string,
  mode?: 'full' | 'collapsed',
): Promise<{ data: AttackPathDTO }> =>
  simpleCall(`${simulationUri(simulationId)}/graph${mode ? `?mode=${mode}` : ''}`, undefined, false);

// Everything that changed in the graph since a given version: upserted nodes/edges/executions, the
// changed finding rows and the recomputed counters, plus the new version to poll from next. An
// unanswerable cursor (run reset, or too far behind) comes back as resyncRequired, and the caller
// re-reads the full graph instead of patching.
export const fetchAttackPathGraphDelta = (
  simulationId: string,
  since: number,
): Promise<{ data: AttackPathDeltaDTO }> =>
  simpleCall(`${simulationUri(simulationId)}/graph/delta?since=${since}`, undefined, false);

// Expand one endpoint into its finding-type and finding nodes (single indexed read).
export const fetchEndpointFindings = (
  simulationId: string,
  ref: string,
): Promise<{ data: AttackPathExpandDTO }> =>
  simpleCall(`${simulationUri(simulationId)}/endpoint/findings?ref=${encodeURIComponent(ref)}`, undefined, false);

// An endpoint's relations: the executions targeting it and the grouped edges into it.
export const fetchEndpointRelations = (
  simulationId: string,
  ref: string,
): Promise<{ data: AttackPathEndpointRelationsDTO }> =>
  simpleCall(`${simulationUri(simulationId)}/endpoint/relations?ref=${encodeURIComponent(ref)}`, undefined, false);

// A page of a widget category's findings for the drawer (issue 5048).
// category is one of credentials | users | files | cves; the value is masked server-side for credentials.
export const fetchFindingsByCategory = (
  simulationId: string,
  category: string,
  page = 0,
  size = 50,
): Promise<{ data: AttackPathFindingPageDTO }> =>
  simpleCall(`${simulationUri(simulationId)}/findings?category=${encodeURIComponent(category)}&page=${page}&size=${size}`);

// One execution's Result & Terminal detail for the drawer: masked command/output, findings, status.
// The execution id is passed as a URL-encoded `ref` query parameter (not a path segment): an
// injector-sourced id ends with the null-agent marker `\0`, and an encoded backslash in the path is
// rejected by the servlet container before it reaches the controller.
export const fetchExecutionDetail = (
  simulationId: string,
  executionId: string,
): Promise<{ data: AttackPathExecutionDetailDTO }> =>
  simpleCall(`${simulationUri(simulationId)}/execution?ref=${encodeURIComponent(executionId)}`, undefined, false);
