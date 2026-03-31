import { type Filter } from '../../../../utils/api-types';

export interface FilterHelpers {
  handleSwitchMode: () => void;
  handleSwitchLocalMode: (key: string) => void;
  handleAddFilterWithEmptyValue: (filter: Filter) => void;
  handleAddSingleValueFilter: (key: string, value: string) => void;
  handleAddMultipleValueFilter: (key: string, values: string[]) => void;
  handleChangeOperatorFilters: (key: string, operator: Filter['operator']) => void;
  handleClearAllFilters: () => void;
  handleRemoveFilterByKey: (key: string) => void;

  handleRemoveFilterById: (filterId: string) => void;
  handleUpdateFilterById: (filterId: string, updates: Partial<Omit<Filter, 'id'>>) => void;
  handleAddFilter: (filterData: Omit<Filter, 'id'>) => void;
  handleSwitchLocalModeById: (filterId: string) => void;
  handleChangeOperatorById: (filterId: string, operator: Filter['operator']) => void;
  handleUpdateValuesById: (filterId: string, values: string[]) => void;
}
