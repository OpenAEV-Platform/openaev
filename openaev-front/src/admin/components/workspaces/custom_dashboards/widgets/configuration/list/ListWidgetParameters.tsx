import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  Select,
  SelectContent,
  SelectHelperText,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { Box } from '@mui/material';
import { useEffect, useState } from 'react';
import { type Control, Controller, type UseFormSetValue, useWatch } from 'react-hook-form';

import { engineSchemas } from '../../../../../../../actions/schema/schema-action';
import { useFormatter } from '../../../../../../../components/i18n';
import type { PropertySchemaDTO, Widget } from '../../../../../../../utils/api-types';
import { type WidgetInputWithoutLayout } from '../../../../../../../utils/api-types-custom';
import { type GroupOption } from '../../../../../../../utils/Option';
import { getBaseEntities } from '../../WidgetUtils';
import WidgetConfigDateAttributeController from '../common/WidgetConfigDateAttributeController';
import WidgetConfigTimeRangeController from '../common/WidgetConfigTimeRangeController';
import getEntityPropertiesListOptions from '../EntityPropertiesListOptions';
import WidgetColumnsCustomizationInput from './WidgetColumnsCustomizationInput';

type Props = {
  widgetType: Widget['widget_type'];
  control: Control<WidgetInputWithoutLayout>;
  setValue: UseFormSetValue<WidgetInputWithoutLayout>;
};

const ListWidgetParameters = (props: Props) => {
  const { t } = useFormatter();
  const [propertySelection, setPropertySelection] = useState<GroupOption[]>([]);
  const [entityColumns, setEntityColumns] = useState<{
    attribute: string;
    label: string;
  }[]>([]);
  const perspective = useWatch({
    control: props.control,
    name: 'widget_config.perspective',
  });
  const columns = useWatch({
    control: props.control,
    name: 'widget_config.columns',
  });
  const entities = [perspective].map(v => getBaseEntities(v.filter)).flat();

  const onColumnSelectionChange = (new_cols: {
    attribute: string;
    label: string;
  }[]) => {
    props.setValue('widget_config.columns', new_cols.map(c => c.attribute));
  };

  useEffect(() => {
    props.setValue('widget_config.widget_configuration_type', 'list');
  }, []);

  // get the entity schema for column names
  useEffect(() => {
    engineSchemas(entities).then((response: { data: PropertySchemaDTO[] }) => {
      const finalOptions = getEntityPropertiesListOptions(
        response.data,
        props.widgetType).map((o) => {
        return {
          ...o,
          label: t(o.label),
        };
      });
      setPropertySelection(finalOptions);
      const newCols = finalOptions
      // we will hide all "side" columns unless it is the tags column or attack patterns column
        .filter(o => !o.id.endsWith('_side') || o.id === 'base_tags_side' || o.id === 'base_attack_patterns_side')
        .map((d) => {
          return {
            attribute: d.id,
            label: d.label,
          };
        });
      setEntityColumns(newCols.toSorted((a, b) => a.label.localeCompare(b.label)));

      if (!columns) {
        onColumnSelectionChange(newCols);
      }
    });
  }, [perspective]);

  return (
    <>
      <Controller
        control={props.control}
        name="widget_config.widget_configuration_type"
        render={({ field }) => (
          <input
            {...field}
            type="hidden"
            value={field.value ?? ''}
          />
        )}
      />
      <Controller
        control={props.control}
        name="widget_config.sorts.0.fieldName"
        render={({ field, fieldState }) => (
          <Box sx={{ mt: 2 }}>
            <Combobox
              options={propertySelection}
              groupBy={option => option.group}
              value={propertySelection.find(o => o.id === field.value) ?? null}
              onValueChange={value => field.onChange((value as { id: string } | null)?.id)}
              getOptionLabel={option => option.label ?? ''}
              isOptionEqualToValue={(option, value) => option.id === value.id}
              required
              error={!!fieldState.error}
            >
              <ComboboxLabel>{t('Sort field')}</ComboboxLabel>
              <ComboboxField>
                <ComboboxInput />
                <ComboboxControls>
                  <ComboboxTrigger />
                </ComboboxControls>
              </ComboboxField>
              <ComboboxContent />
              {fieldState.error?.message ? <ComboboxHelperText>{fieldState.error.message}</ComboboxHelperText> : null}
            </Combobox>
          </Box>
        )}
      />
      <Controller
        control={props.control}
        name="widget_config.sorts.0.direction"
        render={({ field, fieldState }) => (
          <div style={{ marginTop: 16 }}>
            <Select
              value={field.value ?? ''}
              onValueChange={field.onChange}
              error={!!fieldState.error}
              required
              name={field.name}
            >
              <SelectLabel required>{t('Direction')}</SelectLabel>
              <SelectTrigger>
                <SelectValue placeholder={t('Direction')} />
              </SelectTrigger>
              <SelectContent>
                {['ASC', 'DESC'].map(dir => <SelectItem key={dir} value={dir}>{t(dir)}</SelectItem>)}
              </SelectContent>
              {fieldState.error?.message ? <SelectHelperText>{fieldState.error?.message}</SelectHelperText> : null}
            </Select>
          </div>
        )}
      />

      <WidgetConfigDateAttributeController widgetType={props.widgetType} series={[perspective]} />
      <WidgetConfigTimeRangeController />
      <Controller
        control={props.control}
        name="widget_config.columns"
        render={({ field }) => (
          <WidgetColumnsCustomizationInput
            availableColumns={entityColumns}
            defaultColumns={entityColumns}
            value={field.value?.map(v => entityColumns.find(ev => ev.attribute === v) ?? null).filter(v => v !== null) ?? entityColumns}
            onChange={onColumnSelectionChange}
          />
        )}
      />
    </>
  );
};

export default ListWidgetParameters;
