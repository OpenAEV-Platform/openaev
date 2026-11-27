import { simpleCall, simplePostCall } from '../../utils/Action';
import {
  type ScenarioCatalogStateOutput,
  type ScenarioRevisionOutput,
  type ScenarioUpdateInput,
  type ScenarioUpdatePreviewOutput,
  type ScenarioVersionsOutput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import { SCENARIO_URI } from './scenario-actions';

const scenarioUri = (scenarioId: string) => `${SCENARIO_URI}/${scenarioId}`;

export const fetchScenarioVersions = (scenarioId: string): Promise<ScenarioVersionsOutput> => simpleCall(`${scenarioUri(scenarioId)}/versions`)
  .then(({ data }: { data: ScenarioVersionsOutput }) => data);

export const searchScenarioHistory = (scenarioId: string, input: SearchPaginationInput) => simplePostCall(`${scenarioUri(scenarioId)}/history/search`, input);

export const fetchScenarioRevisions = (scenarioId: string): Promise<ScenarioRevisionOutput[]> => simpleCall(`${scenarioUri(scenarioId)}/revisions`)
  .then(({ data }: { data: ScenarioRevisionOutput[] }) => data);

export const fetchScenarioCatalogStates = (scenarioIds: string[]): Promise<Record<string, ScenarioCatalogStateOutput>> => simplePostCall(`${SCENARIO_URI}/catalog-states`, { scenario_ids: scenarioIds })
  .then(({ data }: { data: Record<string, ScenarioCatalogStateOutput> }) => data);

const versionUri = (scenarioId: string, version: string) => `${scenarioUri(scenarioId)}/versions/${encodeURIComponent(version)}`;

// No error toast on previews and applies: the dialog says why it failed, and what to do.
export const previewScenarioUpdate = (scenarioId: string, version: string): Promise<ScenarioUpdatePreviewOutput> => simpleCall(`${versionUri(scenarioId, version)}/preview`, undefined, false)
  .then(({ data }: { data: ScenarioUpdatePreviewOutput }) => data);

export const updateScenario = (scenarioId: string, version: string, input: ScenarioUpdateInput) => simplePostCall(`${versionUri(scenarioId, version)}/updates`, input, undefined, false);

const revisionUri = (scenarioId: string, revisionId: string) => `${scenarioUri(scenarioId)}/revisions/${revisionId}`;

export const previewScenarioRollback = (scenarioId: string, revisionId: string): Promise<ScenarioUpdatePreviewOutput> => simpleCall(`${revisionUri(scenarioId, revisionId)}/preview`, undefined, false)
  .then(({ data }: { data: ScenarioUpdatePreviewOutput }) => data);

export const rollbackScenario = (scenarioId: string, revisionId: string, input: ScenarioUpdateInput) => simplePostCall(`${revisionUri(scenarioId, revisionId)}/rollbacks`, input, undefined, false);
