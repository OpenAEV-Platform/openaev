import {
  Select,
  SelectContent,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { DateTimePicker } from '@mui/x-date-pickers';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { CUSTOM_TIME_RANGE, getTimeRangeItems } from './widgets/configuration/common/TimeRangeUtils';

interface Props {
  handleTimeRange: (data: string) => void;
  handleStartDate: (data: string) => void;
  handleEndDate: (data: string) => void;
  timeRangeValue: string | undefined;
  startDateValue: string | undefined;
  endDateValue: string | undefined;
}

const TimeRangeFilters: FunctionComponent<Props> = ({ handleTimeRange, handleStartDate, handleEndDate, timeRangeValue, startDateValue, endDateValue }) => {
  // Standard hooks
  const { t } = useFormatter();

  const timeRangeItems = getTimeRangeItems();

  return (
    <>
      <Select
        value={timeRangeValue}
        onValueChange={(next) => {
          handleTimeRange(next);
        }}
      >
        <SelectLabel>{t('Time range')}</SelectLabel>
        <SelectTrigger>
          <SelectValue placeholder={t('Time range')} />
        </SelectTrigger>
        <SelectContent>
          {timeRangeItems.map(item => (
            <SelectItem key={item.value} value={item.value}>
              {t(item.label_key)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {
        timeRangeValue === CUSTOM_TIME_RANGE && (
          <>
            <DateTimePicker
              views={['year', 'month', 'day']}
              value={startDateValue ? new Date(startDateValue) : null}
              maxDate={new Date(new Date(endDateValue ?? '').setUTCHours(24, 0, 0, 0))}
              onChange={(startDate) => {
                handleStartDate(new Date(new Date(startDate!).setUTCHours(24, 0, 0, 0)).toISOString());
              }}
              slotProps={{
                textField: {
                  variant: 'outlined',
                  size: 'small',
                },
              }}
              label={t('Start date')}
            />
            <DateTimePicker
              views={['year', 'month', 'day']}
              value={endDateValue ? new Date(endDateValue) : null}
              minDate={new Date(new Date(startDateValue ?? '').setUTCHours(24, 0, 0, 0))}
              onChange={(endDate) => {
                handleEndDate(new Date(new Date(endDate!).setUTCHours(24, 0, 0, 0)).toISOString());
              }}
              slotProps={{
                textField: {
                  variant: 'outlined',
                  size: 'small',
                },
              }}
              label={t('End date')}
            />
          </>
        )
      }
    </>
  );
};

export default TimeRangeFilters;
