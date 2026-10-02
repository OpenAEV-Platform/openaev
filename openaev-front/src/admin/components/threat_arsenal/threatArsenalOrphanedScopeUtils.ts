import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { type FilterGroup } from '../../../utils/api-types';

// Quick "purge orphans" shortcut: narrow the list to the dead "question mark"
// cards and turn on select-all so the floating Delete action purges them at
// once. A question-mark card is an action with NO injector AND NO payload:
//  - no injector (`action_injectors` empty) => its injector was removed, so it
//    can never run;
//  - no payload (`action_payload_status` empty) => this excludes manually
//    created payloads, which have a payload (hence a real icon) and run fine
//    even when momentarily unlinked from an injector.
// Scoping via filters keeps the bulk delete from touching healthy actions.
const ORPHANED_SCOPE_FILTER_KEYS = ['action_injectors', 'action_payload_status'];

// The button is a toggle (#8071): its state is derived from the filters, so it
// also reflects a scope restored from local storage or built by hand.
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
    // Leaving the orphan scope: drop the select-all it turned on, otherwise the
    // floating Delete action would target every healthy action of the list.
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
