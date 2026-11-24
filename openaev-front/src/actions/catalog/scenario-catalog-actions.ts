import { type Page } from '../../components/common/queryable/Page';
import { simplePostCall } from '../../utils/Action';
import {
  type CatalogInstallAllOutput,
  type CatalogInstallOutput,
  type CatalogScenarioOutput,
  type ChainedScenarioInstallInput,
  type SearchPaginationInput,
  type TimeBasedScenarioInstallInput,
} from '../../utils/api-types';
import { type CatalogFacetCounts } from './catalog-helper';
import { CATALOG_URI, catalogVersionUri } from './catalog-uri';

export const searchCatalogScenarios = (input: SearchPaginationInput): Promise<{ data: Page<CatalogScenarioOutput> }> => simplePostCall(`${CATALOG_URI}/scenarios/search`, input);

export const fetchCatalogScenarioFacetCounts = (input: SearchPaginationInput): Promise<CatalogFacetCounts> => simplePostCall(`${CATALOG_URI}/scenarios/facet-counts`, input)
  .then(({ data }: { data: CatalogFacetCounts }) => data);

export const installCatalogScenario = (
  scenario: CatalogScenarioOutput,
  input: TimeBasedScenarioInstallInput | ChainedScenarioInstallInput,
): Promise<CatalogInstallOutput> => simplePostCall(
  `${catalogVersionUri('scenarios', scenario.catalog_scenario_external_id, scenario.catalog_scenario_version)}/install`,
  input,
).then(({ data }: { data: CatalogInstallOutput }) => data);

export const installAllCatalogScenarios = (): Promise<CatalogInstallAllOutput> => simplePostCall(`${CATALOG_URI}/scenarios/install-all`)
  .then(({ data }: { data: CatalogInstallAllOutput }) => data);
