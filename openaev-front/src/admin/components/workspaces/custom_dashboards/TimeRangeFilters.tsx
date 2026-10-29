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
import { toUtcMidnightIso, utcMidnightToLocalDay } from '../../../../utils/Time';
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
            {/* Both bounds are stored as UTC midnight of the picked day and shown as that day, in every time zone */}
            <DateField
              value={startDateValue ? utcMidnightToLocalDay(startDateValue) : null}
              maxDate={endDateValue ? utcMidnightToLocalDay(endDateValue, 1) : undefined}
              onChange={(startDate) => {
                if (!startDate) return;
                handleStartDate(toUtcMidnightIso(startDate));
              }}
              label={t('Start date')}
            />
            <DateField
              value={endDateValue ? utcMidnightToLocalDay(endDateValue) : null}
              minDate={startDateValue ? utcMidnightToLocalDay(startDateValue, 1) : undefined}
              onChange={(endDate) => {
                if (!endDate) return;
                handleEndDate(toUtcMidnightIso(endDate));
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
