import { MenuItem, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { DatePicker } from '@mui/x-date-pickers';
import { Controller, useFormContext, useWatch } from 'react-hook-form';

import { useFormatter } from '../../../../../../../components/i18n';
import { CUSTOM_TIME_RANGE, getTimeRangeItemsWithDefault } from './TimeRangeUtils';

const WidgetConfigTimeRangeController = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { control } = useFormContext();
  const widgetTimeRange = useWatch({
    control: control,
    name: 'widget_config.time_range',
  });

  const timeRangeItems = getTimeRangeItemsWithDefault();

  return (
    <>
      <Controller
        control={control}
        name="widget_config.time_range"
        defaultValue={timeRangeItems[0].value}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            select
            variant="standard"
            fullWidth
            label={t('Time range')}
            sx={{ mt: 2 }}
            value={field.value ?? ''}
            onChange={e => field.onChange(e.target.value)}
            error={!!fieldState.error}
            helperText={fieldState.error?.message}
            required
          >
            {timeRangeItems.map(timeRange => <MenuItem key={timeRange.value} value={timeRange.value}>{t(timeRange.label_key)}</MenuItem>)}
          </TextField>
        )}
      />
      {
        widgetTimeRange === CUSTOM_TIME_RANGE && (
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: theme.spacing(2),
          }}
          >
            <Controller
              control={control}
              name="widget_config.start"
              render={({ field, fieldState }) => (
                <DatePicker
                  label={t('Start date')}
                  sx={{ mt: 2 }}
                  value={field.value ? new Date(field.value) : null}
                  onChange={date => field.onChange(date?.toISOString() ?? '')}
                  slotProps={{
                    textField: {
                      required: widgetTimeRange === CUSTOM_TIME_RANGE,
                      fullWidth: true,
                      error: !!fieldState.error,
                      helperText: fieldState.error?.message,
                      variant: 'standard',
                    },
                  }}
                />
              )}
            />
            <Controller
              control={control}
              name="widget_config.end"
              render={({ field, fieldState }) => (
                <DatePicker
                  label={t('End date')}
                  sx={{ mt: 2 }}
                  value={field.value ? new Date(field.value) : null}
                  onChange={date => field.onChange(date?.toISOString() ?? '')}
                  slotProps={{
                    textField: {
                      required: widgetTimeRange === CUSTOM_TIME_RANGE,
                      fullWidth: true,
                      error: !!fieldState.error,
                      helperText: fieldState.error?.message,
                      variant: 'standard',
                    },
                  }}
                />
              )}
            />
          </div>
        )
      }
    </>
  );
};

export default WidgetConfigTimeRangeController;
