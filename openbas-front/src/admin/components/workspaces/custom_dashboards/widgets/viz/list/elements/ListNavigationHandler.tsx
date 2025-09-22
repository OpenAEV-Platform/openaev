import qs from 'qs';
import { type NavigateFunction } from 'react-router';

import { buildSearchPagination } from '../../../../../../../../components/common/queryable/QueryableUtils';
import {
  ATOMIC_BASE_URL,
  ENDPOINT_BASE_URL,
  SCENARIO_BASE_URL,
  SIMULATION_BASE_URL,
} from '../../../../../../../../constants/BaseUrls';
import {
  type EsBase,
  type EsFinding,
  type EsInject, type EsInjectExpectation,
  type EsVulnerableEndpoint,
} from '../../../../../../../../utils/api-types';
import { getTargetTypeFromInjectExpectation } from './ListColumnConfig';

type NavigationHandler = (element: EsBase, navigate: NavigateFunction) => void;

const getInjectDetailUrl = (injectElement: EsInject): string => {
  let injectUrl = `${ATOMIC_BASE_URL}/${injectElement.base_id}`;
  if (injectElement.base_simulation_side != null && injectElement.inject_execution_date != null) {
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

const navigationHandlers: Record<string, NavigationHandler> = {
  'endpoint': (element, navigate) => {
    navigate(`${ENDPOINT_BASE_URL}/${element.base_id}`);
  },

  'vulnerable-endpoint': (element, navigate) => {
    const craftedFilter = btoa(qs.stringify({
      ...buildSearchPagination({
        filterGroup: {
          mode: 'and',
          filters: [
            {
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
    navigate(`${ENDPOINT_BASE_URL}/${(element as EsVulnerableEndpoint).vulnerable_endpoint_id}?query=${craftedFilter}`);
  },

  'scenario': (element, navigate) => {
    navigate(`${SCENARIO_BASE_URL}/${element.base_id}`);
  },

  'simulation': (element, navigate) => {
    navigate(`${SIMULATION_BASE_URL}/${element.base_id}`);
  },

  'inject': (element, navigate) => {
    navigate(getInjectDetailUrl(element as EsInject));
  },

  'expectation-inject': (element, navigate) => {
    const expectation = element as EsInjectExpectation;
    const injectUrl = expectation.base_simulation_side != null
      ? `${SIMULATION_BASE_URL}/${expectation.base_simulation_side}/injects/${expectation.base_inject_side}`
      : `${ATOMIC_BASE_URL}/${expectation.base_inject_side}`;
    const target = getTargetTypeFromInjectExpectation(expectation);
    navigate(`${injectUrl}?expectation_id=${expectation.base_id}&target=${target.type}`);
  },

  'finding': (element, navigate) => {
    const findingElement = element as EsFinding;
    const craftedFilter = btoa(qs.stringify({
      ...buildSearchPagination({
        filterGroup: {
          mode: 'and',
          filters: [
            {
              key: 'finding_type',
              operator: 'eq',
              mode: 'or',
              values: [findingElement.finding_type ?? ''],
            },
          ],
        },
        textSearch: findingElement.finding_value ?? '',
      }),
      key: 'atm-findings',
    }, { allowEmptyArrays: true }));

    const baseUrl = (findingElement.base_simulation_side != null && findingElement.base_simulation_side != '')
      ? `${SIMULATION_BASE_URL}/${findingElement.base_simulation_side}/injects/${findingElement.base_inject_side}/findings`
      : `${ATOMIC_BASE_URL}/${findingElement.base_inject_side}/findings`;

    navigate(`${baseUrl}?query=${craftedFilter}&open=${findingElement.base_id}`);
  },
};

export default navigationHandlers;
