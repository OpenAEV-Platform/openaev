import qs from 'qs';

import { generateFilterId } from '../../../../../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../../../../../components/common/queryable/QueryableUtils';
import {
  ASSET_BASE_URL,
  ASSET_GROUP_BASE_URL,
  ATOMIC_BASE_URL,
  FINDING_BASE_URL,
  SCENARIO_BASE_URL,
  SIMULATION_BASE_URL,
  TEAM_BASE_URL,
} from '../../../../../../../../constants/BaseUrls';
import {
  type EsBase,
  type EsInject, type EsInjectExpectation,
  type EsVulnerableEndpoint,
} from '../../../../../../../../utils/api-types';
import { getTargetTypeFromInjectExpectation } from './ListColumnConfig';

// URL builders (not navigate callbacks): rows render as real router links so
// ctrl/cmd+click and middle-click open the target in a new tab everywhere.
type NavigationUrlBuilder = (element: EsBase) => string;

const getInjectDetailUrl = (injectElement: EsInject): string => {
  let injectUrl = `${ATOMIC_BASE_URL}/${injectElement.base_id}`;
  if (injectElement.base_simulation_side != null && injectElement.execution_date != null) {
    injectUrl = `${SIMULATION_BASE_URL}/${injectElement.base_simulation_side}/injects/${injectElement.base_id}`;
  } else if (injectElement.base_simulation_side != null) {
    const craftedFilter = btoa(qs.stringify({
      ...buildSearchPagination({ textSearch: injectElement.inject_title }),
      key: `${injectElement.base_simulation_side}-injects`,
    }));
    injectUrl = `${SIMULATION_BASE_URL}/${injectElement.base_simulation_side}/injects?query=${craftedFilter}`;
  } else if (injectElement.base_scenario_side != null) {
    const craftedFilter = btoa(qs.stringify({
      ...buildSearchPagination({ textSearch: injectElement.inject_title }),
      key: `${injectElement.base_scenario_side}-injects`,
    }));
    injectUrl = `${SCENARIO_BASE_URL}/${injectElement.base_scenario_side}/injects?query=${craftedFilter}`;
  }
  return injectUrl;
};

const navigationUrlBuilders: Record<string, NavigationUrlBuilder> = {
  'asset': element => `${ASSET_BASE_URL}/${element.base_id}`,

  'asset-group': element => `${ASSET_GROUP_BASE_URL}/${element.base_id}`,

  'team': element => `${TEAM_BASE_URL}/${element.base_id}`,

  'vulnerable-endpoint': (element) => {
    const craftedFilter = btoa(qs.stringify({
      ...buildSearchPagination({
        filterGroup: {
          mode: 'and',
          filters: [
            {
              id: generateFilterId(),
              key: 'finding_type',
              operator: 'eq',
              mode: 'or',
              values: ['CVE'],
            },
          ],
        },
      }),
      key: 'endpoint-findings',
    }, { allowEmptyArrays: true }));
    return `${ASSET_BASE_URL}/${(element as EsVulnerableEndpoint).vulnerable_endpoint_id}?query=${craftedFilter}`;
  },

  'scenario': element => `${SCENARIO_BASE_URL}/${element.base_id}`,

  'simulation': element => `${SIMULATION_BASE_URL}/${element.base_id}`,

  'inject': element => getInjectDetailUrl(element as EsInject),

  'expectation-inject': (element) => {
    const expectation = element as EsInjectExpectation;
    const injectUrl = expectation.base_simulation_side == null
      ? `${ATOMIC_BASE_URL}/${expectation.base_inject_side}`
      : `${SIMULATION_BASE_URL}/${expectation.base_simulation_side}/injects/${expectation.base_inject_side}`;
    const target = getTargetTypeFromInjectExpectation(expectation);
    return `${injectUrl}?expectation_id=${expectation.base_id}&target=${target.type}`;
  },

  // Findings have their own full-page overview: always land there instead of
  // the owning inject (the overview exposes the related injects as pivots).
  'finding': element => `${FINDING_BASE_URL}/${element.base_id}`,
};

/** Returns the detail URL for an ES element, or null when the entity has no overview. */
export const getNavigationUrl = (element: EsBase): string | null => {
  const builder = navigationUrlBuilders[element.base_entity ?? ''];
  return builder ? builder(element) : null;
};

export default navigationUrlBuilders;
