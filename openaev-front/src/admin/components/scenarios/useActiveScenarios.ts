import { useState } from 'react';

import { bulkDeleteScenarios, fetchScenarioAuthorCounts, searchScenarios } from '../../../actions/scenarios/scenario-actions';
import { useAuthorFacetOptions } from '../../../components/common/facets/ContractFacets';
import { initSorting } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Scenario, type SearchPaginationInput } from '../../../utils/api-types';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';

export const ACTIVE_SCENARIO_FILTER_NAMES = [
  'scenario_category',
  'scenario_kill_chain_phases',
  'scenario_name',
  'scenario_platforms',
  'scenario_recurrence',
  'scenario_severity',
  'scenario_tags',
  'scenario_type',
  'scenario_updated_at',
  'scenario_author',
];

const useActiveScenarios = () => {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [reloadCount, setReloadCount] = useState<number>(0);

  const {
    queryableHelpers,
    searchPaginationInput,
    setSearchPaginationInput,
  } = useQueryableWithLocalStorage('scenarios', buildSearchPagination({ sorts: initSorting('scenario_updated_at', 'DESC') }));

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchScenarios(input).finally(() => setLoading(false));
  };

  const totalElements = queryableHelpers.paginationHelpers.getTotalElements();
  const authorOptions = useAuthorFacetOptions(fetchScenarioAuthorCounts, searchPaginationInput);
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<Scenario>('scenario', scenarios, totalElements);

  const isSelected = (scenario: Scenario) =>
    (selectAll && !(scenario.scenario_id in (deSelectedElements || {})))
    || scenario.scenario_id in (selectedElements || {});

  const bulkDelete = () => {
    bulkDeleteScenarios({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      scenario_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      scenario_ids_to_ignore: Object.keys(deSelectedElements),
    }).then((result) => {
      const deletedIds: string[] = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setScenarios(scenarios.filter(s => !deletedIds.includes(s.scenario_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  const removeScenario = (scenarioId: string) => {
    setScenarios(scenarios.filter(e => (e.scenario_id !== scenarioId)));
    setSearchPaginationInput(prev => ({
      ...prev,
      size: prev.size - 1,
    }));
  };

  return {
    scenarios,
    setScenarios,
    loading,
    search,
    reload: () => setReloadCount(count => count + 1),
    reloadCount,
    queryableHelpers,
    searchPaginationInput,
    totalElements,
    authorOptions,
    removeScenario,
    selection: {
      selectAll,
      numberOfSelectedElements,
      isSelected,
      onToggleEntity,
      handleToggleSelectAll,
      handleClearSelectedElements,
    },
    bulkDelete,
  };
};

export default useActiveScenarios;
