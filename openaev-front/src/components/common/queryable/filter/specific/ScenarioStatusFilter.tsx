import { Autocomplete, MenuItem, Select, TextField } from '@mui/material';
import { type FunctionComponent } from 'react';

import { SCENARIO_NOT_SCHEDULED_STATUS, SCENARIO_SCHEDULED_STATUS } from '../../../../../admin/components/scenarios/scenario/ScenarioStatus';
import { type PropertySchemaDTO } from '../../../../../utils/api-types';
import { type Option } from '../../../../../utils/Option';
import { useFormatter } from '../../../../i18n';
import { type FilterHelpers } from '../FilterHelpers';
import { OperatorKeyValues } from '../FilterUtils';

const ScenarioStatusFilter: FunctionComponent<{
  propertySchema: PropertySchemaDTO;
  helpers: FilterHelpers;
}> = ({
  propertySchema,
  helpers,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const operators = ['eq'];

  const options: Option[] = [
    {
      id: SCENARIO_SCHEDULED_STATUS,
      label: t(SCENARIO_SCHEDULED_STATUS),
    },
    {
      id: SCENARIO_NOT_SCHEDULED_STATUS,
      label: t(SCENARIO_NOT_SCHEDULED_STATUS),
    },
  ];

  const onChange = (newValue: Option | null) => {
    if (newValue) {
      helpers.handleAddSingleValueFilter(propertySchema.schema_property_name, newValue.id);
    }
  };

  return (
    <>
      <Select
        value={operators[0]}
        label="Operator"
        fullWidth
        style={{ marginBottom: 15 }}
      >
        {operators.map(value => (
          <MenuItem key={value} value={value}>
            {t(OperatorKeyValues[value])}
          </MenuItem>
        ))}
      </Select>
      <Autocomplete
        selectOnFocus
        openOnFocus
        autoHighlight
        noOptionsText={t('No available options')}
        options={options}
        getOptionLabel={option => option.label ?? ''}
        isOptionEqualToValue={(option, v) => option.id === v.id}
        onChange={(_event, newValue) => {
          onChange(newValue);
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

export default ScenarioStatusFilter;
