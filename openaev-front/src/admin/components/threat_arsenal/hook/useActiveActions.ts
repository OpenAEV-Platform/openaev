import { useState } from 'react';

import { searchThreatArsenalActions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../../utils/api-types';
import { useBulkOperationsFinishedCount } from '../../../../utils/bulkOperations';
import { MITRE_FILTER_KEY } from '../../common/filters/MitreFilter';

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

// The installed actions of the Active tab: their search and the changes made on the page.
const useActiveActions = () => {
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

  const hasActiveFilters = (searchPaginationInput.filterGroup?.filters?.length ?? 0) > 0
    || !!searchPaginationInput.textSearch;
  const resetFilters = () => {
    queryableHelpers.filterHelpers.handleClearAllFilters();
    queryableHelpers.textSearchHelpers.handleTextSearch('');
  };

  const prependActions = (results: ThreatArsenalAction[]) => setThreatArsenalActions(prev => [...results, ...prev]);
  const addAction = (result: ThreatArsenalAction) => prependActions([result]);
  const replaceAction = (result: ThreatArsenalAction, replacedId = result.injector_contract_id) => setThreatArsenalActions(prev => prev
    .map(a => (a.injector_contract_id === replacedId ? result : a)));
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
    hasActiveFilters,
    resetFilters,
  };
};

export default useActiveActions;
