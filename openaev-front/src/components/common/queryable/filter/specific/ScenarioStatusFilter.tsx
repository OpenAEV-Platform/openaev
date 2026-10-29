import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { SCENARIO_NOT_SCHEDULED_STATUS, SCENARIO_SCHEDULED_STATUS } from '../../../../../admin/components/scenarios/scenario/ScenarioStatus';
import { type Filter, type PropertySchemaDTO } from '../../../../../utils/api-types';
import { type Option } from '../../../../../utils/Option';
import { useFormatter } from '../../../../i18n';
import { type FilterHelpers } from '../FilterHelpers';
import { OperatorKeyValues } from '../FilterUtils';

const ScenarioStatusFilter: FunctionComponent<{
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
      helpers.handleUpdateValuesById(filter.id, [newValue.id]);
    }
  };

  return (
    <>
      <div style={{ marginBottom: 15 }}>
        <Select value={operators[0]}>
          <SelectTrigger className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {operators.map(value => (
              <SelectItem key={value} value={value}>
                {t(OperatorKeyValues[value])}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <Combobox<Option>
        openOnFocus
        options={options}
        value={options.find(opt => filter.values?.includes(opt.id)) ?? null}
        onValueChange={newValue => onChange(newValue as Option | null)}
        getOptionLabel={option => option.label ?? ''}
        isOptionEqualToValue={(option, v) => option.id === v.id}
      >
        <ComboboxLabel>{t(propertySchema.schema_property_name)}</ComboboxLabel>
        <ComboboxField>
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

export default ScenarioStatusFilter;
