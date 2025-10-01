import { Switch } from '@mui/material';
import { type ChangeEvent, type FunctionComponent, useEffect, useState } from 'react';

import { type FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { buildEmptyFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../../components/i18n';
import { type FilterGroup } from '../../../../utils/api-types';
import { INJECTOR_CONTRACT_INJECTOR_FILTER_KEY, INJECTOR_CONTRACT_PLAYERS_ONLY } from './constants';

interface Props {
  filterHelpers: FilterHelpers;
  filterGroup?: FilterGroup;
}

const InjectorContractSwitchFilter: FunctionComponent<Props> = ({
  filterHelpers,
  filterGroup,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const retrieveFilter = () => {
    return filterGroup?.filters?.find(f => f.key === INJECTOR_CONTRACT_INJECTOR_FILTER_KEY);
  };

  const isChecked = () => {
    const filter = retrieveFilter();
    if (!filter) {
      return false;
    }
    return filter.values?.some(v => INJECTOR_CONTRACT_PLAYERS_ONLY.includes(v));
  };

  const [enablePlayerFilter, setEnablePlayerFilter] = useState(isChecked);

  const onChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { checked } = event.target;
    setEnablePlayerFilter(checked);
    if (checked) {
      const filter = retrieveFilter();
      if (!filter) {
        filterHelpers.handleAddFilterWithEmptyValue(buildEmptyFilter(INJECTOR_CONTRACT_INJECTOR_FILTER_KEY, 'contains'));
      }
      filterHelpers.handleAddMultipleValueFilter(
        INJECTOR_CONTRACT_INJECTOR_FILTER_KEY,
        INJECTOR_CONTRACT_PLAYERS_ONLY,
      );
    } else {
      filterHelpers.handleAddMultipleValueFilter(
        INJECTOR_CONTRACT_INJECTOR_FILTER_KEY,
        [],
      );
    }
  };

  useEffect(() => {
    const isFilterChecked = isChecked();
    if (enablePlayerFilter !== isFilterChecked) {
      setEnablePlayerFilter(isFilterChecked);
    }
  }, [filterGroup]);

  return (
    <>
      <Switch
        key={enablePlayerFilter ? 'checked' : 'unchecked'}
        checked={enablePlayerFilter}
        onChange={onChange}
        color="primary"
        size="small"
      />
      <span>{t('Targeting Players only')}</span>
    </>
  );
};

export default InjectorContractSwitchFilter;
