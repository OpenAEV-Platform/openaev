import { Autocomplete, MenuItem, Select, TextField } from '@mui/material';
import { type FunctionComponent } from 'react';

import { type Filter, type PropertySchemaDTO } from '../../../../../utils/api-types';
import { type Option } from '../../../../../utils/Option';
import { useFormatter } from '../../../../i18n';
import { type FilterHelpers } from '../FilterHelpers';
import { OperatorKeyValues } from '../FilterUtils';

// Ids MUST match the backend ScenarioUtils engine-type values (Time-based / Chained / Autonomous).
export const SCENARIO_TYPE_TIME_BASED = 'Time-based';
export const SCENARIO_TYPE_CHAINED = 'Chained';
export const SCENARIO_TYPE_AUTONOMOUS = 'Autonomous';

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
    {
      id: SCENARIO_TYPE_AUTONOMOUS,
      label: t(SCENARIO_TYPE_AUTONOMOUS),
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
      <Autocomplete
        multiple
        selectOnFocus
        openOnFocus
        autoHighlight
        noOptionsText={t('No available options')}
        options={options}
        getOptionLabel={option => option.label ?? ''}
        isOptionEqualToValue={(option, v) => option.id === v.id}
        value={value}
        onChange={(_event, newValue) => {
          helpers.handleUpdateValuesById(filter.id, newValue.map(o => o.id));
        }}
        renderInput={paramsInput => (
          <TextField
            {...paramsInput}
            label={t(propertySchema.schema_property_name)}
            variant="outlined"
            size="small"
          />
        )}
      />
    </>
  );
};

export default ScenarioTypeFilter;
