import { useState } from 'react';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { fetchAttackPatterns } from '../../../../actions/AttackPattern';
import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import {
  bulkDeleteThreatArsenalActions,
  fetchThreatArsenalAuthorCounts,
  searchThreatArsenalActions,
} from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { useAuthorOptions } from '../../../../components/common/facets/ContractFacets';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import {
  type AttackPattern,
  type Domain,
  type InjectorContractAuthorCountOutput,
  type SearchPaginationInput,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import { useBulkOperationsFinishedCount } from '../../../../utils/bulkOperations';
import { useAppDispatch } from '../../../../utils/hooks';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useEntityToggle from '../../../../utils/hooks/useEntityToggle';
import useDomainIconFilter from '../../common/domains/useDomainIconFilter';
import { MITRE_FILTER_KEY } from '../../common/filters/MitreFilter';
import { hasOrphanedScopeFilters, toggleOrphanedScope } from '../threatArsenalOrphanedScopeUtils';
import useThreatArsenalFacetCounts from '../useThreatArsenalFacetCounts';

export const ACTIVE_FILTER_NAMES = [
  'action_injectors',
  'action_platforms',
  'action_domains',
  'action_tags',
  'action_payload_status',
  'action_updated_at',
  'action_author',
  MITRE_FILTER_KEY,
];

// The installed actions of the Active tab: search, facets, selection and the changes made on the page.
const useActiveActions = () => {
  const { t } = useFormatter();
  const countLabel = useCountLabel();
  const [threatArsenalActions, setThreatArsenalActions] = useState<ThreatArsenalAction[]>([]);
  const [loading, setLoading] = useState<boolean>(false);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'threat-arsenal',
    buildSearchPagination({
      sorts: [{
        property: 'action_updated_at',
        direction: 'DESC',
      }],
    }),
  );
  const fetchActions = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchThreatArsenalActions({
      ...input,
      group_by_primary_domain: true,
    }).finally(() => setLoading(false));
  };
  // Bulk deletes run detached: reload the actions each time one finishes.
  const finishedBulkOperations = useBulkOperationsFinishedCount();
  const totalElements = queryableHelpers.paginationHelpers.getTotalElements();

  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
  });
  const attackPatterns: AttackPattern[] = useHelper((helper: AttackPatternHelper) => helper.getAttackPatterns());
  const domainOptions: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const { iconBarOrderedDomains } = useDomainIconFilter({
    domainOptions,
    searchPaginationInput,
    queryableHelpers,
    apiPrefix: 'threat_arsenals',
    domainFilterKey: 'action_domains',
  });
  const facetCounts = useThreatArsenalFacetCounts(searchPaginationInput);
  const authorOptions = useAuthorOptions(() => fetchThreatArsenalAuthorCounts(buildSearchPagination({}))
    .then(({ data }: { data: InjectorContractAuthorCountOutput[] }) => data.map(row => ({
      value: row.author ?? '',
      label: row.author_name ?? row.author ?? '',
      type: row.author_type,
    }))));

  const hasActiveFilters = (searchPaginationInput.filterGroup?.filters?.length ?? 0) > 0
    || !!searchPaginationInput.textSearch;
  const resetFilters = () => {
    queryableHelpers.filterHelpers.handleClearAllFilters();
    queryableHelpers.textSearchHelpers.handleTextSearch('');
  };

  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<ThreatArsenalAction>('injector_contract', threatArsenalActions, totalElements);
  const partiallySelected = (!selectAll && numberOfSelectedElements > 0)
    || (selectAll && Object.keys(deSelectedElements ?? {}).length > 0);
  const isChecked = (action: ThreatArsenalAction) => (selectAll && !(action.injector_contract_id in (deSelectedElements || {})))
    || action.injector_contract_id in (selectedElements || {});

  const isOrphanedScopeActive = hasOrphanedScopeFilters(searchPaginationInput.filterGroup);
  const toggleOrphaned = () => toggleOrphanedScope({
    isActive: isOrphanedScopeActive,
    filterHelpers: queryableHelpers.filterHelpers,
    selectAll,
    handleToggleSelectAll,
    handleClearSelectedElements,
  });

  // Fire and forget, like every massive operation: the top bar reports progress, the list reloads when it ends.
  const bulkDelete = () => {
    const input = {
      ...searchPaginationInput,
      injector_contract_ids_to_process: selectAll ? [] : Object.keys(selectedElements),
      injector_contract_ids_to_ignore: selectAll ? Object.keys(deSelectedElements) : [],
    };
    handleClearSelectedElements();
    bulkDeleteThreatArsenalActions(input).catch(() => {});
  };

  const prependActions = (results: ThreatArsenalAction[]) => setThreatArsenalActions(prev => [...results, ...prev]);
  const addAction = (result: ThreatArsenalAction) => prependActions([result]);
  const replaceAction = (result: ThreatArsenalAction) => setThreatArsenalActions(prev => prev
    .map(a => (a.injector_contract_id === result.injector_contract_id ? result : a)));
  const removeAction = (actionId: string) => setThreatArsenalActions(prev => prev.filter(a => a.injector_contract_id !== actionId));

  return {
    threatArsenalActions,
    setThreatArsenalActions,
    prependActions,
    addAction,
    replaceAction,
    removeAction,
    loading,
    fetchActions,
    finishedBulkOperations,
    queryableHelpers,
    searchPaginationInput,
    totalElements,
    attackPatterns,
    domainOptions,
    iconBarOrderedDomains,
    facetCounts,
    authorOptions,
    hasActiveFilters,
    resetFilters,
    isOrphanedScopeActive,
    toggleOrphaned,
    bulkDelete,
    selection: {
      selectedElements,
      deSelectedElements,
      selectAll,
      count: numberOfSelectedElements,
      label: numberOfSelectedElements === 0
        ? t('Select all')
        : countLabel(numberOfSelectedElements, '1 action selected', '{count} actions selected'),
      checked: partiallySelected ? 'indeterminate' as const : selectAll,
      isChecked,
      onToggleEntity,
      handleToggleSelectAll,
      handleClearSelectedElements,
    },
  };
};

export default useActiveActions;
