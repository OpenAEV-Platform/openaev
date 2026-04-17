import { type AxiosResponse } from 'axios';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { fetchDomainCounts } from '../../../../actions/domains/domain-actions';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { type QueryableHelpers } from '../../../../components/common/queryable/QueryableHelpers';
import {
  type Domain,
  type InjectorContractDomainCountOutput,
  type SearchPaginationInput,
} from '../../../../utils/api-types';
import { type Error as APIError, notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';
import { buildOrderedDomains } from '../../workspaces/custom_dashboards/widgets/viz/domains/SecurityDomainsWidgetUtils';
import buildIconBarElements from './DomainsIcons';
import { type IconBarElement } from './IconBar-model';

const DEFAULT_DOMAIN_FILTER_KEY = 'injector_contract_domains';

interface UseDomainIconFilterParams {
  domainOptions: Domain[];
  searchPaginationInput?: SearchPaginationInput;
  queryableHelpers?: QueryableHelpers;
  domainFilterKey?: string;
  apiPrefix?: string;
}

interface UseDomainIconFilterResult {
  selectedDomains: string[];
  domainCounts: Record<string, number>;
  handleDomainClick: (domainId: string) => void;
  iconBarOrderedDomains: IconBarElement[];
}

const useDomainIconFilter = ({
  domainOptions,
  searchPaginationInput,
  queryableHelpers,
  domainFilterKey = DEFAULT_DOMAIN_FILTER_KEY,
  apiPrefix = 'injector_contracts',
}: UseDomainIconFilterParams): UseDomainIconFilterResult => {
  const [selectedDomains, setSelectedDomains] = useState<string[]>([]);
  const [domainCounts, setDomainCounts] = useState<Record<string, number>>({});

  const filterHelpers = queryableHelpers?.filterHelpers;

  const handleDomainClick = useCallback((domainId: string) => {
    if (!filterHelpers) return;

    const nextSelectedDomains = selectedDomains.includes(domainId)
      ? selectedDomains.filter(id => id !== domainId)
      : [...selectedDomains, domainId];

    if (nextSelectedDomains.length === 0) {
      filterHelpers.handleRemoveFilterByKey(domainFilterKey);
      return;
    }

    const hasDomainFilter = searchPaginationInput?.filterGroup?.filters?.some(({ key }) => key === domainFilterKey) ?? false;

    if (hasDomainFilter) {
      filterHelpers.handleAddMultipleValueFilter(domainFilterKey, nextSelectedDomains);
    } else {
      filterHelpers.handleAddFilterWithEmptyValue({
        id: generateFilterId(),
        key: domainFilterKey,
        operator: 'contains',
        values: nextSelectedDomains,
        mode: 'or',
      });
    }
  }, [domainFilterKey, filterHelpers, searchPaginationInput?.filterGroup?.filters, selectedDomains]);

  useEffect(() => {
    if (searchPaginationInput) {
      fetchDomainCounts(apiPrefix, searchPaginationInput)
        .then((response: AxiosResponse<InjectorContractDomainCountOutput[]>) => {
          const data = response?.data;

          if (Array.isArray(data)) {
            const countsMap = data.reduce((acc, curr) => {
              if (curr.domain) {
                acc[curr.domain] = curr.count ?? 0;
              }
              return acc;
            }, {} as Record<string, number>);

            setDomainCounts(countsMap);
          } else {
            notifyErrorHandler({
              status: 400,
              message: 'Invalid data format received',
            });
          }
        })
        .catch((error: unknown) => {
          notifyErrorHandler(error as APIError);
        });
    }
  }, [searchPaginationInput]);

  useEffect(() => {
    const domainFilter = searchPaginationInput?.filterGroup?.filters?.find(
      ({ key }) => key === domainFilterKey,
    );

    setSelectedDomains(Array.isArray(domainFilter?.values) ? (domainFilter.values as string[]) : []);
  }, [domainFilterKey, searchPaginationInput?.filterGroup]);

  const iconBarElements = useMemo(
    () => buildIconBarElements(domainOptions, handleDomainClick, selectedDomains, domainCounts),
    [domainOptions, domainCounts, handleDomainClick, selectedDomains],
  );

  const iconBarOrderedDomains = useMemo(
    () => buildOrderedDomains(iconBarElements),
    [iconBarElements],
  );

  return {
    selectedDomains,
    domainCounts,
    handleDomainClick,
    iconBarOrderedDomains,
  };
};

export default useDomainIconFilter;
