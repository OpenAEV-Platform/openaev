import {
  Select,
  SelectContent,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import DateField from '../../../../components/fields/DateField';
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
      {/* The library Select renders NO wrapper of its own — unlike Combobox,
          which wraps its parts in a flex column. Its label and its trigger are
          therefore siblings of whatever holds them, and in the grid this row
          uses they landed in two different cells, one beside the other. The
          wrapper keeps them together. */}
      <div>
        <Select
          value={timeRangeValue}
          onValueChange={(next) => {
            handleTimeRange(next);
          }}
        >
          <SelectLabel>{t('Time range')}</SelectLabel>
          <SelectTrigger style={{ minWidth: 120 }}>
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
      </div>
      {
        timeRangeValue === CUSTOM_TIME_RANGE && (
          <>
            <DateField
              value={startDateValue ? new Date(startDateValue) : null}
              maxDate={new Date(new Date(endDateValue ?? '').setUTCHours(24, 0, 0, 0))}
              onChange={(startDate) => {
                if (!startDate) return;
                handleStartDate(new Date(new Date(startDate).setUTCHours(24, 0, 0, 0)).toISOString());
              }}
              label={t('Start date')}
            />
            <DateField
              value={endDateValue ? new Date(endDateValue) : null}
              minDate={new Date(new Date(startDateValue ?? '').setUTCHours(24, 0, 0, 0))}
              onChange={(endDate) => {
                if (!endDate) return;
                handleEndDate(new Date(new Date(endDate).setUTCHours(24, 0, 0, 0)).toISOString());
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
