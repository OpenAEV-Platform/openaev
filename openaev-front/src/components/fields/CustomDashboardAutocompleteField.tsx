import { useContext, useEffect, useState } from 'react';

import {
  searchCustomDashboardAsOptions,
  searchCustomDashboardAsOptionsByResourceId,
} from '../../actions/custom_dashboards/customdashboard-action';
import { useFormatter } from '../../components/i18n';
import type { Option } from '../../utils/Option';
import { AbilityContext } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import AutocompleteField from './AutocompleteField';

interface Props {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  scenarioOrSimulationId?: string;
  disabled?: boolean;
  /** Adds an italic "Platform default" option mapping to an empty value (hardcoded dashboard). */
  withPlatformDefault?: boolean;
}

const CustomDashboardAutocompleteField = ({ label, value, onChange, required = false, scenarioOrSimulationId, disabled, withPlatformDefault = false }: Props) => {
  const ability = useContext(AbilityContext);
  const { t } = useFormatter();
  const [options, setOptions] = useState<Option[]>([]);

  const platformDefaultOption: Option = {
    id: '',
    label: t('Platform default'),
    italic: true,
  };

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
    if (withPlatformDefault && t('Platform default').toLowerCase().includes(searchText.toLowerCase())) {
      options = [platformDefaultOption, ...options];
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
