import { Autocomplete, MenuItem, TextField } from '@mui/material';
import { useEffect, useState } from 'react';
import { type Control, Controller, useFormContext, type UseFormSetValue, useWatch } from 'react-hook-form';

import { engineSchemas } from '../../../../../../../actions/schema/schema-action';
import { useFormatter } from '../../../../../../../components/i18n';
import { type PropertySchemaDTO, type Widget } from '../../../../../../../utils/api-types';
import { type GroupOption } from '../../../../../../../utils/Option';
import { getAvailableModes, getBaseEntities, getLimit, type WidgetInputWithoutLayout } from '../../WidgetUtils';
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
  const entities = series.map(v => getBaseEntities(v.filter)).flat();

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
  useEffect(() => {
    if (availableModes.length === 1) {
      setValue('widget_config.mode', availableModes[0]); // If only one mode is available, hide the field and set it automatically
    }
  }, []);

  const hasLimit = getLimit(widgetType);

  // -- HANDLE widget config type --
  useEffect(() => {
    switch (mode) {
      case 'structural':
        setValue('widget_config.widget_configuration_type', 'structural-histogram');
        break;
      case 'temporal':
        setValue('widget_config.widget_configuration_type', 'temporal-histogram');
        break;
      default:
        setValue('widget_config.widget_configuration_type', 'structural-histogram');
    }
  }, [mode]);

  // -- HANDLE FIELDS --
  const [fieldOptions, setFieldOptions] = useState<GroupOption[]>([]);

  // Watch the current field value to validate it when options change
  const currentField = useWatch({
    control,
    name: mode === 'temporal' ? 'widget_config.date_attribute' : 'widget_config.field',
  });

  useEffect(() => {
    // If in structural mode with multiple entities, only show common fields
    if (mode === 'structural' && entities.length > 1) {
      // Fetch schemas for each entity separately
      Promise.all(entities.map(entity => engineSchemas([entity])))
        .then((responses: { data: PropertySchemaDTO[] }[]) => {
          // Get field options for each entity
          const optionsByEntity = responses.map(response =>
            getEntityPropertiesListOptions(
              response.data,
              widgetType,
              d => d.schema_property_type !== 'instant',
            ),
          );

          // Create Sets upfront for better performance
          const entityFieldSets = optionsByEntity.map(options =>
            new Set(options.map(o => o.id)),
          );

          // Find intersection of fields (fields that exist in ALL entities)
          const commonFieldIds = optionsByEntity.length > 0
            ? optionsByEntity[0].map(o => o.id).filter(id =>
                entityFieldSets.every(fieldSet => fieldSet.has(id)),
              )
            : [];

          // Filter to only include common fields
          const finalOptions = optionsByEntity.length > 0
            ? optionsByEntity[0]
                .filter(o => commonFieldIds.includes(o.id))
                .map((o) => {
                  return {
                    ...o,
                    label: t(o.label),
                  };
                })
            : [];

          setFieldOptions(finalOptions);

          // Clear field value if it's no longer in the available options
          if (currentField && !finalOptions.some(o => o.id === currentField)) {
            setValue('widget_config.field', '');
          } else if (finalOptions.length === 1) {
            setValue('widget_config.field', finalOptions[0].id);
          }
        });
    } else {
      // Single entity or temporal mode - show all fields
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

        // Clear field value if it's no longer in the available options
        const fieldName = mode === 'temporal' ? 'widget_config.date_attribute' : 'widget_config.field';
        if (currentField && !finalOptions.some(o => o.id === currentField)) {
          setValue(fieldName, '');
        } else if (finalOptions.length === 1) {
          setValue(fieldName, finalOptions[0].id); // If only one option is available, hide the field and set it automatically
        }
      });
    }
  }, [mode, entities.length, currentField, widgetType, setValue, t]);

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
              <TextField
                {...field}
                select
                variant="standard"
                fullWidth
                label={t('Mode')}
                sx={{ mt: 2 }}
                value={field.value ?? ''}
                error={!!fieldState.error}
                helperText={fieldState.error?.message}
                onChange={e => field.onChange(e.target.value)}
                required
              >
                {availableModes.map(mode => <MenuItem key={mode} value={mode}>{t(mode)}</MenuItem>)}
              </TextField>
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
                <Autocomplete
                  options={fieldOptions}
                  groupBy={option => option.group}
                  value={fieldOptions.find(o => o.id === field.value) ?? null}
                  onChange={(_, value) => field.onChange(value?.id)}
                  getOptionLabel={option => option.label ?? ''}
                  isOptionEqualToValue={(option, value) => option.id === value.id}
                  renderInput={params => (
                    <TextField
                      {...params}
                      label={mode === 'temporal' ? t('Date attribute') : t('Breakdown by')}
                      variant="standard"
                      fullWidth
                      sx={{ mt: 2 }}
                      error={!!fieldState.error}
                      helperText={fieldState.error?.message}
                      required
                    />
                  )}
                  freeSolo={false}
                />
              );
            }}
          />
        )}
      {mode === 'temporal' && (
        <Controller
          control={control}
          name="widget_config.interval"
          render={({ field, fieldState }) => (
            <TextField
              {...field}
              select
              variant="standard"
              fullWidth
              label={t('Interval')}
              sx={{ mt: 2 }}
              value={field.value ?? ''}
              onChange={e => field.onChange(e.target.value)}
              error={!!fieldState.error}
              helperText={fieldState.error?.message}
              required
            >
              <MenuItem value="day">{t('Day')}</MenuItem>
              <MenuItem value="week">{t('Week')}</MenuItem>
              <MenuItem value="month">{t('Month')}</MenuItem>
              <MenuItem value="quarter">{t('Quarter')}</MenuItem>
              <MenuItem value="year">{t('Year')}</MenuItem>
            </TextField>
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
