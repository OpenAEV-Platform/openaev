import { Kayaking } from '@mui/icons-material';
import { type CSSProperties } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { fetchScenarios } from '../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../actions/scenarios/scenario-helper';
import { useHelper } from '../../store';
import { type Scenario } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { type Option } from '../../utils/Option';
import EntityMultiSelectField from './EntityMultiSelectField';

interface Props {
  name: string;
  label: string;
  placeholder?: string;
  style?: CSSProperties;
  disabled?: boolean;
  required?: boolean;
}

const ScenarioFieldController = ({ name, label, placeholder, style, disabled = false, required = false }: Props) => {
  const { control } = useFormContext();
  const dispatch = useAppDispatch();

  const scenarios = useHelper((helper: ScenariosHelper) => helper.getScenarios());
  useDataLoader(() => {
    dispatch(fetchScenarios());
  });

  const options: Option[] = (scenarios ?? []).map((scenario: Scenario) => ({
    id: scenario.scenario_id,
    label: scenario.scenario_name,
  }));

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { value, onChange }, fieldState: { error } }) => (
        <EntityMultiSelectField
          label={label}
          options={options}
          fieldValue={value ?? []}
          fieldOnChange={onChange}
          icon={<Kayaking />}
          error={error}
          placeholder={placeholder}
          style={style}
          disabled={disabled}
          required={required}
        />
      )}
    />
  );
};

export default ScenarioFieldController;
