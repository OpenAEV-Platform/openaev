import { useContext, useEffect, useState } from 'react';

import {
  searchCustomDashboardAsOptions,
  searchCustomDashboardAsOptionsByResourceId,
} from '../../actions/custom_dashboards/customdashboard-action';
import type { Option } from '../../utils/Option';
import { AbilityContext } from '../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import AutocompleteField from './AutocompleteField';

interface Props {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  scenarioOrSimulationId?: string;
  disabled?: boolean;
}

const CustomDashboardAutocompleteField = ({ label, value, onChange, required = false, scenarioOrSimulationId, disabled }: Props) => {
  const ability = useContext(AbilityContext);
  const [options, setOptions] = useState<Option[]>([]);

  const searchDashboardOptions = async (searchText: string) => {
    let options: Option[] = [];

    if (ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS)) {
      // get all the dashboards
      const res = await searchCustomDashboardAsOptions(searchText);
      options = res.data as Option[];
    } else if (scenarioOrSimulationId) {
      // get the dashboards from scenario or simulation
      const res = await searchCustomDashboardAsOptionsByResourceId(scenarioOrSimulationId);
      options = res.data as Option[];
    }
    setOptions(options);
  };

  useEffect(() => {
    searchDashboardOptions('');
  }, []);

  return (
    <AutocompleteField
      label={label}
      value={value}
      required={required}
      options={options}
      onChange={v => onChange(v ?? '')}
      onInputChange={searchDashboardOptions}
      variant="standard"
      disabled={disabled}
    />
  );
};

export default CustomDashboardAutocompleteField;
