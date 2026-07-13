import { simpleCall } from '../../utils/Action';
import type { AttackPathDTO, AttackPathEndpointRelationsDTO, AttackPathExpandDTO, AttackPathSimSummaryRow } from '../../utils/api-types';

// Attack-path execution-store POC (issue 6647), gated by the ATTACK_PATH preview feature.
// The tenant prefix is added centrally by Action.buildUri, so these use the plain /api paths.
const ATTACK_PATH_URI = '/api/attack-path';

const simulationUri = (simulationId: string) => `${ATTACK_PATH_URI}/simulations/${simulationId}`;

// The simulations that have attack-path data in the caller's tenant (id + endpoint/execution counts),
// for the picker.
export const fetchAttackPathSimulations = (): Promise<{ data: AttackPathSimSummaryRow[] }> =>
  simpleCall(`${ATTACK_PATH_URI}/simulations`);

// Rebuild the whole graph. Without a mode the backend auto-selects full or collapsed on size;
// passing 'full' or 'collapsed' forces it.
export const fetchAttackPathGraph = (
  simulationId: string,
  mode?: 'full' | 'collapsed',
): Promise<{ data: AttackPathDTO }> =>
  simpleCall(`${simulationUri(simulationId)}/graph${mode ? `?mode=${mode}` : ''}`);

// Expand one endpoint into its finding-type and finding nodes (single indexed read).
export const fetchEndpointFindings = (
  simulationId: string,
  ref: string,
): Promise<{ data: AttackPathExpandDTO }> =>
  simpleCall(`${simulationUri(simulationId)}/endpoint/findings?ref=${encodeURIComponent(ref)}`);

// An endpoint's relations: the executions targeting it and the grouped edges into it.
export const fetchEndpointRelations = (
  simulationId: string,
  ref: string,
): Promise<{ data: AttackPathEndpointRelationsDTO }> =>
  simpleCall(`${simulationUri(simulationId)}/endpoint/relations?ref=${encodeURIComponent(ref)}`);
