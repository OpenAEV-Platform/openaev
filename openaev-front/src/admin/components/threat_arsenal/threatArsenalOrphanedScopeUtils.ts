import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { type FilterGroup } from '../../../utils/api-types';

// Orphans have no injector (they can never run) and no payload (so manual payloads stay out).
const ORPHANED_SCOPE_FILTER_KEYS = ['action_injectors', 'action_payload_status'];

// Read from the filters, so a scope restored from local storage also shows as on.
export const hasOrphanedScopeFilters = (filterGroup: FilterGroup | undefined): boolean => {
  const filters = filterGroup?.filters ?? [];
  return ORPHANED_SCOPE_FILTER_KEYS.every(key => filters
    .some(filter => filter.key === key && filter.operator === 'empty'));
};

interface ToggleOrphanedScopeParams {
  isActive: boolean;
  filterHelpers: FilterHelpers;
  selectAll: boolean;
  handleToggleSelectAll: () => void;
  handleClearSelectedElements: () => void;
}

export const toggleOrphanedScope = ({
  isActive,
  filterHelpers,
  selectAll,
  handleToggleSelectAll,
  handleClearSelectedElements,
}: ToggleOrphanedScopeParams) => {
  ORPHANED_SCOPE_FILTER_KEYS.forEach(key => filterHelpers.handleRemoveFilterByKey(key));
  if (isActive) {
    // Otherwise the select-all it turned on would delete every healthy action.
    handleClearSelectedElements();
    return;
  }
  ORPHANED_SCOPE_FILTER_KEYS.forEach(key => filterHelpers.handleAddFilterWithEmptyValue({
    id: generateFilterId(),
    key,
    operator: 'empty',
    values: [],
    mode: 'and',
  }));
  if (!selectAll) {
    handleToggleSelectAll();
  }
};
