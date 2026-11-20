import { simpleCall, simplePostCall } from '../../utils/Action';
import {
  type CatalogInstallOutput,
  type CatalogScenarioOutput,
  type CatalogScenarioVersionOutput,
  type ChainedScenarioInstallInput,
  type TimeBasedScenarioInstallInput,
} from '../../utils/api-types';
import { CATALOG_URI, catalogItemUri, catalogVersionUri } from './catalog-uri';

export const fetchCatalogScenarios = (): Promise<CatalogScenarioOutput[]> => simpleCall(`${CATALOG_URI}/scenarios`)
  .then(({ data }: { data: CatalogScenarioOutput[] }) => data);

export const fetchCatalogScenarioVersions = (externalId: string): Promise<CatalogScenarioVersionOutput[]> => simpleCall(`${catalogItemUri('scenarios', externalId)}/versions`)
  .then(({ data }: { data: CatalogScenarioVersionOutput[] }) => data);

export const installCatalogScenario = (
  scenario: CatalogScenarioOutput,
  input: TimeBasedScenarioInstallInput | ChainedScenarioInstallInput,
): Promise<CatalogInstallOutput> => simplePostCall(
  `${catalogVersionUri('scenarios', scenario.catalog_scenario_external_id, scenario.catalog_scenario_version)}/install`,
  input,
).then(({ data }: { data: CatalogInstallOutput }) => data);
