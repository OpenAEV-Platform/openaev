import { MenuItem, Select } from '@mui/material';
import {
  Combobox,
  ComboboxChips,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { type Filter, type PropertySchemaDTO } from '../../../../../utils/api-types';
import { type Option } from '../../../../../utils/Option';
import { useFormatter } from '../../../../i18n';
import { type FilterHelpers } from '../FilterHelpers';
import { OperatorKeyValues } from '../FilterUtils';

// Ids MUST match the backend ScenarioUtils engine-type values (Time-based / Chained). Autonomy is
// a launch-time MODE, not a scenario type.
export const SCENARIO_TYPE_TIME_BASED = 'Time-based';
export const SCENARIO_TYPE_CHAINED = 'Chained';

const ScenarioTypeFilter: FunctionComponent<{
  propertySchema: PropertySchemaDTO;
  helpers: FilterHelpers;
  filter: Filter;
}> = ({
  propertySchema,
  helpers,
  filter,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const operators = ['eq'];

  const options: Option[] = [
    {
      id: SCENARIO_TYPE_TIME_BASED,
      label: t(SCENARIO_TYPE_TIME_BASED),
    },
    {
      id: SCENARIO_TYPE_CHAINED,
      label: t(SCENARIO_TYPE_CHAINED),
    },
  ];

  const value = options.filter(opt => filter.values?.includes(opt.id));

  return (
    <>
      <Select
        value={operators[0]}
        label="Operator"
        fullWidth
        style={{ marginBottom: 15 }}
      >
        {operators.map(op => (
          <MenuItem key={op} value={op}>
            {t(OperatorKeyValues[op])}
          </MenuItem>
        ))}
      </Select>
      <Combobox<Option>
        multiple
        openOnFocus
        options={options}
        value={value}
        onValueChange={(newValue) => {
          helpers.handleUpdateValuesById(filter.id, (newValue as Option[]).map(o => o.id));
        }}
        getOptionLabel={option => option.label ?? ''}
        isOptionEqualToValue={(option, v) => option.id === v.id}
      >
        <ComboboxLabel>{t(propertySchema.schema_property_name)}</ComboboxLabel>
        <ComboboxField>
          <ComboboxChips />
          <ComboboxInput />
          <ComboboxControls>
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent emptyMessage={t('No available options')} />
      </Combobox>
    </>
  );
};

export default ScenarioTypeFilter;
