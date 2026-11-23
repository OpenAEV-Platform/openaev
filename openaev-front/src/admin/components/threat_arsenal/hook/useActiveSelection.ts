import { bulkDeleteThreatArsenalActions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { useFormatter } from '../../../../components/i18n';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import useEntityToggle from '../../../../utils/hooks/useEntityToggle';
import { hasOrphanedScopeFilters, toggleOrphanedScope } from '../threatArsenalOrphanedScopeUtils';

// The actions picked on the Active tab, and what the page does with them at once.
const useActiveSelection = (
  actions: ThreatArsenalAction[],
  totalElements: number,
  searchPaginationInput: SearchPaginationInput,
  filterHelpers: FilterHelpers,
) => {
  const { t } = useFormatter();
  const countLabel = useCountLabel();
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<ThreatArsenalAction>('injector_contract', actions, totalElements);
  const partiallySelected = (!selectAll && numberOfSelectedElements > 0)
    || (selectAll && Object.keys(deSelectedElements).length > 0);
  const isChecked = (action: ThreatArsenalAction) => (selectAll && !(action.injector_contract_id in deSelectedElements))
    || action.injector_contract_id in selectedElements;

  const isOrphanedScopeActive = hasOrphanedScopeFilters(searchPaginationInput.filterGroup);
  const toggleOrphaned = () => toggleOrphanedScope({
    isActive: isOrphanedScopeActive,
    filterHelpers,
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

  return {
    selectedElements,
    deSelectedElements,
    selectAll,
    count: numberOfSelectedElements,
    label: numberOfSelectedElements === 0
      ? t('Select all')
      : countLabel(numberOfSelectedElements, '1 action selected', '{count} actions selected'),
    checked: partiallySelected ? 'indeterminate' as const : selectAll,
    isOnlyOne: selectAll
      ? Object.keys(deSelectedElements).length === totalElements - 1
      : numberOfSelectedElements === 1,
    isChecked,
    onToggleEntity,
    handleToggleSelectAll,
    handleClearSelectedElements,
    isOrphanedScopeActive,
    toggleOrphaned,
    bulkDelete,
  };
};

export default useActiveSelection;
