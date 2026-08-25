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
import { Box, TextField } from '@mui/material';
import { useEffect, useState } from 'react';
import { type Control, Controller, useFormContext, type UseFormSetValue, useWatch } from 'react-hook-form';

import { engineSchemas } from '../../../../../../../actions/schema/schema-action';
import { useFormatter } from '../../../../../../../components/i18n';
import { type PropertySchemaDTO, type Widget } from '../../../../../../../utils/api-types';
import { type WidgetInputWithoutLayout } from '../../../../../../../utils/api-types-custom';
import { type GroupOption } from '../../../../../../../utils/Option';
import { getAvailableModes, getBaseEntities, getLimit } from '../../WidgetUtils';
import WidgetConfigDateAttributeController from '../common/WidgetConfigDateAttributeController';
import WidgetConfigTimeRangeController from '../common/WidgetConfigTimeRangeController';
import getEntityPropertiesListOptions from '../EntityPropertiesListOptions';

type Props = {
  widgetType: Widget['widget_type'];
  control: Control<WidgetInputWithoutLayout>;
  setValue: UseFormSetValue<WidgetInputWithoutLayout>;
  showOnlyTitle?: boolean;
};

const HistogramParameters = ({ widgetType, control, setValue }: Props) => {
// Standard hooks
  const { t } = useFormatter();

  // -- WATCH --
  const mode = useWatch({
    control,
    name: 'widget_config.mode',
  });
  const widgetConfigurationType = useWatch({
    control,
    name: 'widget_config.widget_configuration_type',
  });
  const widgetTimeRange = useWatch({
    control,
    name: 'widget_config.time_range',
  });
  const series = useWatch({
    control,
    name: 'widget_config.series',
  });
  const startDate = useWatch({
    control,
    name: 'widget_config.start',
  });
  const endDate = useWatch({
    control,
    name: 'widget_config.end',
  });
  const entities = series.flatMap(v => getBaseEntities(v.filter));

  const { setError, clearErrors } = useFormContext();

  useEffect(() => {
    if (widgetTimeRange === 'CUSTOM' && !startDate) {
      setError('widget_config.start', {
        type: 'manual',
        message: t('Start date is required'),
      });
    } else {
      clearErrors('widget_config.start');
    }
  }, [widgetTimeRange, startDate]);

  useEffect(() => {
    if (widgetTimeRange === 'CUSTOM' && !endDate) {
      setError('widget_config.end', {
        type: 'manual',
        message: t('End date is required'),
      });
    } else {
      clearErrors('widget_config.end');
    }
  }, [widgetTimeRange, endDate]);

  // -- HANDLE MODE --
  const availableModes = getAvailableModes(widgetType);

  const setModeAndConfigType = (newMode: string) => {
    setValue('widget_config.mode', newMode as 'temporal' | 'structural');
    switch (newMode) {
      case 'temporal':
        setValue('widget_config.widget_configuration_type', 'temporal-histogram');
        break;
      case 'structural':
      default:
        setValue('widget_config.widget_configuration_type', 'structural-histogram');
    }
  };

  useEffect(() => {
    const expectedConfigType = mode === 'temporal' ? 'temporal-histogram' : 'structural-histogram';

    // Auto-set only when mode is hidden (single-mode widgets).
    if (availableModes.length === 1 && (!mode || !availableModes.includes(mode))) {
      const defaultMode = availableModes[0];
      setValue('widget_config.mode', defaultMode);
      setValue('widget_config.widget_configuration_type', defaultMode === 'temporal' ? 'temporal-histogram' : 'structural-histogram');
      return;
    }

    // Keep discriminator in sync when user explicitly selected a valid mode.
    if (mode && availableModes.includes(mode) && widgetConfigurationType !== expectedConfigType) {
      setValue('widget_config.widget_configuration_type', expectedConfigType);
    }
  }, [availableModes, mode, setValue, widgetConfigurationType]);

  const hasLimit = getLimit(widgetType);

  // -- HANDLE FIELDS --
  const [fieldOptions, setFieldOptions] = useState<GroupOption[]>([]);

  useEffect(() => {
    engineSchemas(entities).then((response: { data: PropertySchemaDTO[] }) => {
      const finalOptions = getEntityPropertiesListOptions(
        response.data,
        widgetType,
        d => mode === 'temporal' ? d.schema_property_type === 'instant' : d.schema_property_type !== 'instant')
        .map((o) => {
          return {
            ...o,
            label: t(o.label),
          };
        });
      setFieldOptions(finalOptions);
      if (finalOptions.length === 1) {
        setValue('widget_config.field', finalOptions[0].id); // If only one option is available, hide the field and set it automatically
      }
    });
  }, [mode]);

  return (
    <>
      <Controller
        control={control}
        name="widget_config.widget_configuration_type"
        render={({ field }) => (
          <input
            {...field}
            type="hidden"
            value={field.value ?? ''}
          />
        )}
      />
      {availableModes.length > 1
        && (
          <Controller
            control={control}
            name="widget_config.mode"
            render={({ field, fieldState }) => (
              <div style={{ marginTop: 16 }}>
                <Select
                  value={field.value ?? ''}
                  onValueChange={setModeAndConfigType}
                  error={!!fieldState.error}
                  required
                  name={field.name}
                >
                  <SelectLabel required>{t('Mode')}</SelectLabel>
                  <SelectTrigger>
                    <SelectValue placeholder={t('Mode')} />
                  </SelectTrigger>
                  <SelectContent>
                    {availableModes.map(mode => <SelectItem key={mode} value={mode}>{t(mode)}</SelectItem>)}
                  </SelectContent>
                  {fieldState.error?.message ? <SelectHelperText>{fieldState.error?.message}</SelectHelperText> : null}
                </Select>
              </div>
            )}
          />
        )}
      {hasLimit && (
        <Controller
          control={control}
          name="widget_config.limit"
          defaultValue={10}
          render={({ field, fieldState }) => (
            <TextField
              {...field}
              variant="standard"
              fullWidth
              type="number"
              label={t('Number of results')}
              sx={{ mt: 2 }}
              value={field.value}
              onChange={e => field.onChange(e.target.value === '' ? '' : Number(e.target.value))}
              error={!!fieldState.error}
              helperText={fieldState.error?.message}
              required
            />
          )}
        />
      )}
      {fieldOptions.length > 1
        && (
          <Controller
            control={control}
            name={mode === 'temporal' ? 'widget_config.date_attribute' : 'widget_config.field'}
            render={({ field, fieldState }) => {
              return (
                <Box sx={{ mt: 2 }}>
                  <Combobox
                    options={fieldOptions}
                    groupBy={option => option.group}
                    value={fieldOptions.find(o => o.id === field.value) ?? null}
                    onValueChange={value => field.onChange((value as { id: string } | null)?.id)}
                    getOptionLabel={option => option.label ?? ''}
                    isOptionEqualToValue={(option, value) => option.id === value.id}
                    required
                    error={!!fieldState.error}
                  >
                    <ComboboxLabel>{mode === 'temporal' ? t('Date attribute') : t('Breakdown by')}</ComboboxLabel>
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
              );
            }}
          />
        )}
      {mode === 'temporal' && (
        <Controller
          control={control}
          name="widget_config.interval"
          render={({ field, fieldState }) => (
            <div style={{ marginTop: 16 }}>
              <Select
                value={field.value ?? ''}
                onValueChange={field.onChange}
                error={!!fieldState.error}
                required
                name={field.name}
              >
                <SelectLabel required>{t('Interval')}</SelectLabel>
                <SelectTrigger>
                  <SelectValue placeholder={t('Interval')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="day">{t('Day')}</SelectItem>
                  <SelectItem value="week">{t('Week')}</SelectItem>
                  <SelectItem value="month">{t('Month')}</SelectItem>
                  <SelectItem value="quarter">{t('Quarter')}</SelectItem>
                  <SelectItem value="year">{t('Year')}</SelectItem>
                </SelectContent>
                {fieldState.error?.message ? <SelectHelperText>{fieldState.error?.message}</SelectHelperText> : null}
              </Select>
            </div>
          )}
        />
      )}
      {
        mode === 'structural' && (
          <WidgetConfigDateAttributeController widgetType={widgetType} series={series} />
        )
      }
      <WidgetConfigTimeRangeController />
    </>
  );
};

export default HistogramParameters;
