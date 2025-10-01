import { type Filter } from '../../../../utils/api-types';

export interface FilterHelpers {
  handleSwitchMode: () => void;
  handleAddFilterWithEmptyValue: (filter: Filter) => void;
  handleAddSingleValueFilter: (key: string, value: string) => void;
  handleAddMultipleValueFilter: (key: string, values: string[]) => void;
  handleChangeOperatorFilters: (key: string, operator: Filter['operator']) => void;
  handleClearAllFilters: () => void;
  handleRemoveFilterByKey: (key: string) => void;
}
