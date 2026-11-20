import { simpleCall, simplePostCall } from '../../utils/Action';
import {
  type CatalogInstallOutput,
  type CatalogScenarioOutput,
  type CatalogScenarioVersionOutput,
  type ChainedScenarioInstallInput,
  type ScenarioTargetsInput,
} from '../../utils/api-types';

const CATALOG_URI = '/api/catalog';

export const fetchCatalogScenarios = (): Promise<CatalogScenarioOutput[]> => simpleCall(`${CATALOG_URI}/scenarios`)
  .then(({ data }: { data: CatalogScenarioOutput[] }) => data);

export const fetchCatalogScenarioVersions = (externalId: string): Promise<CatalogScenarioVersionOutput[]> => simpleCall(`${CATALOG_URI}/scenarios/${encodeURIComponent(externalId)}/versions`)
  .then(({ data }: { data: CatalogScenarioVersionOutput[] }) => data);

export const installCatalogScenario = (
  scenario: CatalogScenarioOutput,
  input: ScenarioTargetsInput | ChainedScenarioInstallInput,
): Promise<CatalogInstallOutput> => simplePostCall(
  `${CATALOG_URI}/scenarios/${encodeURIComponent(scenario.catalog_scenario_external_id)}/versions/${encodeURIComponent(scenario.catalog_scenario_version)}/install`,
  input,
).then(({ data }: { data: CatalogInstallOutput }) => data);
