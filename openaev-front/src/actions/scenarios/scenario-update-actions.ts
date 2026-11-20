import { simpleCall, simplePostCall } from '../../utils/Action';
import {
  type ScenarioChangeOutput,
  type ScenarioRevisionOutput,
  type ScenarioUpdateInput,
  type ScenarioVersionsOutput,
} from '../../utils/api-types';
import { SCENARIO_URI } from './scenario-actions';

const scenarioUri = (scenarioId: string) => `${SCENARIO_URI}/${scenarioId}`;

export const fetchScenarioVersions = (scenarioId: string): Promise<ScenarioVersionsOutput> => simpleCall(`${scenarioUri(scenarioId)}/versions`)
  .then(({ data }: { data: ScenarioVersionsOutput }) => data);

export const fetchScenarioRevisions = (scenarioId: string): Promise<ScenarioRevisionOutput[]> => simpleCall(`${scenarioUri(scenarioId)}/revisions`)
  .then(({ data }: { data: ScenarioRevisionOutput[] }) => data);

const versionUri = (scenarioId: string, version: string) => `${scenarioUri(scenarioId)}/versions/${encodeURIComponent(version)}`;

export const previewScenarioUpdate = (scenarioId: string, version: string): Promise<ScenarioChangeOutput[]> => simpleCall(`${versionUri(scenarioId, version)}/update-preview`)
  .then(({ data }: { data: ScenarioChangeOutput[] }) => data);

export const updateScenario = (scenarioId: string, version: string, input: ScenarioUpdateInput) => simplePostCall(`${versionUri(scenarioId, version)}/update`, input);

const revisionUri = (scenarioId: string, revisionId: string) => `${scenarioUri(scenarioId)}/revisions/${revisionId}`;

export const previewScenarioRollback = (scenarioId: string, revisionId: string): Promise<ScenarioChangeOutput[]> => simpleCall(`${revisionUri(scenarioId, revisionId)}/rollback-preview`)
  .then(({ data }: { data: ScenarioChangeOutput[] }) => data);

export const rollbackScenario = (scenarioId: string, revisionId: string, input: ScenarioUpdateInput) => simplePostCall(`${revisionUri(scenarioId, revisionId)}/rollback`, input);
