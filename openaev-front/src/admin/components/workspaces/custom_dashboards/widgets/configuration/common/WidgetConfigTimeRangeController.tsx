import {
  Select,
  SelectContent,
  SelectHelperText,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
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
          <div style={{ marginTop: 16 }}>
            <Select
              value={field.value ?? ''}
              onValueChange={field.onChange}
              error={!!fieldState.error}
              required
              name={field.name}
            >
              <SelectLabel required>{t('Time range')}</SelectLabel>
              <SelectTrigger className="w-full">
                <SelectValue placeholder={t('Time range')} />
              </SelectTrigger>
              <SelectContent>
                {timeRangeItems.map(timeRange => <SelectItem key={timeRange.value} value={timeRange.value}>{t(timeRange.label_key)}</SelectItem>)}
              </SelectContent>
              {fieldState.error?.message ? <SelectHelperText>{fieldState.error?.message}</SelectHelperText> : null}
            </Select>
          </div>
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
