import { useState } from 'react';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import {
  bulkDeleteThreatArsenalActions,
  fetchThreatArsenalAuthorCounts,
  searchThreatArsenalActions,
} from '../../../actions/threat_arsenals/threatArsenal-actions';
import { useAuthorFacetOptions } from '../../../components/common/facets/ContractFacets';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../utils/api-types';
import { useBulkOperationsFinishedCount } from '../../../utils/bulkOperations';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import useDomainIconFilter from '../common/domains/useDomainIconFilter';
import useThreatArsenalFacetCounts from './useThreatArsenalFacetCounts';

export const INSTALLED_FILTER_NAMES = [
  'action_injectors',
  'action_platforms',
  'action_domains',
  'action_tags',
  'action_payload_status',
  'action_updated_at',
  'action_author',
];

const useInstalledActions = () => {
  const { t } = useFormatter();
  const [threatArsenalActions, setThreatArsenalActions] = useState<ThreatArsenalAction[]>([]);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'threat-arsenal',
    buildSearchPagination({
      sorts: [{
        property: 'action_updated_at',
        direction: 'DESC',
      }],
    }),
  );

  const [loading, setLoading] = useState<boolean>(true);
  const fetchActions = (input: SearchPaginationInput) => {
    setLoading(true);
    // Loading ends with the results (below): ending it before shows the empty state for a frame.
    // Versions are picked on each card: drop an "all versions" filter a browser may still hold.
    const filterGroup = input.filterGroup
      ? {
          ...input.filterGroup,
          filters: (input.filterGroup.filters ?? []).filter(f => f.key !== 'action_all_versions'),
        }
      : input.filterGroup;
    return searchThreatArsenalActions({
      ...input,
      filterGroup,
      group_by_primary_domain: true,
    }).catch((error) => {
      setLoading(false);
      throw error;
    });
  };
  const setLoadedActions = (actions: ThreatArsenalAction[]) => {
    setThreatArsenalActions(actions);
    setLoading(false);
  };

  // Massive operations (bulk delete) run detached from this screen: reload the page
  // of actions every time one finishes, so the list and the total stop showing the
  // rows that were deleted in the background. Only live transitions bump this count
  // (the history replayed at startup does not), so mounting never triggers a reload.
  const finishedBulkOperations = useBulkOperationsFinishedCount();

  const totalElements = queryableHelpers.paginationHelpers.getTotalElements();

  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<ThreatArsenalAction>(
    'injector_contract',
    threatArsenalActions,
    totalElements,
  );

  const { domainOptions } = useHelper(
    (helper: DomainHelper) => ({ domainOptions: helper.getDomains() }),
  );
  const { iconBarOrderedDomains } = useDomainIconFilter({
    domainOptions,
    searchPaginationInput,
    queryableHelpers,
    apiPrefix: 'threat_arsenals',
    domainFilterKey: 'action_domains',
  });

  // Platform + status counts come from a global aggregation endpoint (never
  // from `threatArsenalActions`, which only holds the currently loaded page and
  // would yield misleading page-bound counts).
  const facetCounts = useThreatArsenalFacetCounts(searchPaginationInput);

  // Full author universe + per-filter counts (backend aggregation), so the
  // sidebar keeps every author visible and greys out the zero-count ones.
  const authorOptions = useAuthorFacetOptions(fetchThreatArsenalAuthorCounts, searchPaginationInput);

  const isSelectedAction = (action: ThreatArsenalAction) =>
    (selectAll && !(action.injector_contract_id in (deSelectedElements || {})))
    || action.injector_contract_id in (selectedElements || {});

  const hasActiveFilters = !!(
    (searchPaginationInput.filterGroup?.filters?.length ?? 0) > 0
    || (searchPaginationInput.textSearch && searchPaginationInput.textSearch.length > 0)
  );

  const handleResetFilters = () => {
    queryableHelpers.filterHelpers.handleClearAllFilters();
    queryableHelpers.textSearchHelpers.handleTextSearch('');
  };

  // Quick "purge orphans" shortcut: narrow the list to the dead "question mark"
  // cards and turn on select-all so the floating Delete action purges them at
  // once. A question-mark card is an action with NO injector AND NO payload:
  //  - no injector (`action_injectors` empty) => its injector was removed, so it
  //    can never run;
  //  - no payload (`action_payload_status` empty) => this excludes manually
  //    created payloads, which have a payload (hence a real icon) and run fine
  //    even when momentarily unlinked from an injector.
  // Scoping via filters keeps the bulk delete from touching healthy actions.
  const handleSelectOrphaned = () => {
    queryableHelpers.filterHelpers.handleRemoveFilterByKey('action_injectors');
    queryableHelpers.filterHelpers.handleRemoveFilterByKey('action_payload_status');
    queryableHelpers.filterHelpers.handleAddFilterWithEmptyValue({
      id: generateFilterId(),
      key: 'action_injectors',
      operator: 'empty',
      values: [],
      mode: 'and',
    });
    queryableHelpers.filterHelpers.handleAddFilterWithEmptyValue({
      id: generateFilterId(),
      key: 'action_payload_status',
      operator: 'empty',
      values: [],
      mode: 'and',
    });
    if (!selectAll) {
      handleToggleSelectAll();
    }
  };

  // Fire and forget, like every other massive operation in the platform: the
  // selection clears immediately, progress is reported by the massive-operations
  // indicator in the top bar, and the list reloads once the operation reaches a
  // terminal state (see `finishedBulkOperations`). Awaiting it would keep the
  // confirmation dialog loading for the whole deletion - minutes on a large scope.
  const bulkDelete = () => {
    const input = {
      ...searchPaginationInput,
      injector_contract_ids_to_process: selectAll ? [] : Object.keys(selectedElements),
      injector_contract_ids_to_ignore: selectAll ? Object.keys(deSelectedElements) : [],
    };
    handleClearSelectedElements();
    // Failures are already surfaced by the shared error notifier, so the rethrow is
    // swallowed here to avoid an unhandled rejection on this detached call.
    bulkDeleteThreatArsenalActions(input).catch(() => {});
  };

  // Former visible caption of the select-all control, now carried by its
  // tooltip and accessible name (the toolbar shows the bare checkbox only).
  // String() narrows the formatter's `string | ReactNode[]` return type for
  // the aria-label: with a numeric placeholder the result is always a string.
  const selectAllLabel = (() => {
    if (numberOfSelectedElements === 0) return t('Select all');
    if (numberOfSelectedElements === 1) return t('1 action selected');
    return String(t('{count} actions selected', { count: numberOfSelectedElements }));
  })();

  // A save that created a version returns the new action: it takes the place of the previous one.
  const replaceAction = (result: ThreatArsenalAction) =>
    setThreatArsenalActions(prev => prev.map(a => (a.injector_contract_id === result.injector_contract_id
      || a.injector_contract_id === result.action_previous_version_id
      ? result
      : a)));
  const prependActions = (results: ThreatArsenalAction[]) => setThreatArsenalActions(prev => [...results, ...prev]);
  const removeAction = (actionId: string) =>
    setThreatArsenalActions(prev => prev.filter(a => a.injector_contract_id !== actionId));

  return {
    threatArsenalActions,
    setThreatArsenalActions: setLoadedActions,
    replaceAction,
    prependActions,
    removeAction,
    loading,
    fetchActions,
    finishedBulkOperations,
    queryableHelpers,
    searchPaginationInput,
    totalElements,
    iconBarOrderedDomains,
    facetCounts,
    authorOptions,
    hasActiveFilters,
    handleResetFilters,
    handleSelectOrphaned,
    selection: {
      selectedElements,
      deSelectedElements,
      selectAll,
      numberOfSelectedElements,
      selectAllLabel,
      isSelectedAction,
      onToggleEntity,
      handleToggleSelectAll,
      handleClearSelectedElements,
    },
    bulkDelete,
  };
};

export default useInstalledActions;
