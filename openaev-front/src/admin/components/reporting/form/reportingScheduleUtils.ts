import { z } from 'zod';

import { type ReportingSchedule, type ReportingScheduleInput } from '../../../../utils/api-types';
import { REPORTING_FORMATS, REPORTING_SCHEDULE_PERIODS, WEEKDAY_LABELS } from '../ReportingFormUtils';
import { type ReportingScheduleFieldsValues } from './ReportingScheduleFields';

const TIME_REGEX = /^\d{2}:\d{2}$/;

/**
 * Backend trigger-time format (ReportingScheduleTimeUtils): DAY = "HH:mm",
 * WEEK = "<ISO day 1-7>-HH:mm", MONTH = "<1-31>-HH:mm", HOUR = unused. The
 * form keeps the day and the time as separate fields and composes here.
 */
const composeTriggerTime = (values: ReportingScheduleFieldsValues): string | undefined => {
  switch (values.schedule_period) {
    case 'HOUR':
      return undefined;
    case 'DAY':
      return values.schedule_time || undefined;
    default:
      return values.schedule_time ? `${values.schedule_day || '1'}-${values.schedule_time}` : undefined;
  }
};

/** Splits a stored trigger time into its day and HH:mm parts. */
const splitTriggerTime = (triggerTime?: string): {
  day?: string;
  time?: string;
} => {
  if (!triggerTime) return {};
  const dash = triggerTime.indexOf('-');
  if (dash <= 0) return { time: triggerTime };
  return {
    day: triggerTime.substring(0, dash),
    time: triggerTime.substring(dash + 1),
  };
};

/** Flat schedule form values -> API input. */
export const scheduleInputFromValues = (values: ReportingScheduleFieldsValues): ReportingScheduleInput => ({
  reporting_schedule_name: values.schedule_name || undefined,
  reporting_schedule_period: values.schedule_period,
  reporting_schedule_time: composeTriggerTime(values),
  reporting_schedule_format: values.schedule_format,
  reporting_schedule_enabled: values.schedule_enabled,
  reporting_schedule_recipient_users: values.schedule_recipient_users,
  reporting_schedule_recipient_emails: values.schedule_recipient_emails,
});

/** Existing schedule -> flat form values (create defaults when absent). */
export const scheduleValuesFromSchedule = (schedule?: ReportingSchedule): ReportingScheduleFieldsValues => {
  const { day, time } = splitTriggerTime(schedule?.reporting_schedule_time);
  return {
    schedule_name: schedule?.reporting_schedule_name ?? '',
    schedule_period: schedule?.reporting_schedule_period ?? 'WEEK',
    schedule_day: day ?? '1',
    schedule_time: time ?? '08:00',
    schedule_format: schedule?.reporting_schedule_format ?? 'PDF',
    schedule_enabled: schedule?.reporting_schedule_enabled ?? true,
    schedule_recipient_users: schedule?.reporting_schedule_recipient_users ?? [],
    schedule_recipient_emails: schedule?.reporting_schedule_recipient_emails ?? [],
  };
};

/**
 * Human summary of a schedule's firing time, e.g. "Every week at Monday 08:00"
 * or "Every month at 15 08:00" (raw stored format is "<day>-HH:mm").
 */
export const describeScheduleTime = (
  schedule: Pick<ReportingSchedule, 'reporting_schedule_period' | 'reporting_schedule_time'>,
  t: (key: string) => string,
): string | undefined => {
  if (schedule.reporting_schedule_period === 'HOUR' || !schedule.reporting_schedule_time) {
    return undefined;
  }
  const { day, time } = splitTriggerTime(schedule.reporting_schedule_time);
  if (!day || !time) return schedule.reporting_schedule_time;
  if (schedule.reporting_schedule_period === 'WEEK') {
    const weekday = WEEKDAY_LABELS[Number.parseInt(day, 10) - 1];
    return weekday ? `${t(weekday)} ${time}` : schedule.reporting_schedule_time;
  }
  return `${day} ${time}`;
};

/** Zod shape of the schedule fields, reused by the wizard's global schema. */
export const buildScheduleFieldsSchema = () => ({
  schedule_name: z.string(),
  schedule_period: z.enum(REPORTING_SCHEDULE_PERIODS),
  schedule_day: z.string(),
  schedule_time: z.string(),
  schedule_format: z.enum(REPORTING_FORMATS),
  schedule_enabled: z.boolean(),
  schedule_recipient_users: z.array(z.string()),
  schedule_recipient_emails: z.array(z.string()),
});

/** Time is required (HH:mm) for every period except HOUR. */
export const validateScheduleTime = (
  values: Pick<ReportingScheduleFieldsValues, 'schedule_period' | 'schedule_time'>,
  ctx: z.RefinementCtx,
  t: (key: string) => string,
  path: string = 'schedule_time',
) => {
  if (values.schedule_period !== 'HOUR' && !TIME_REGEX.test(values.schedule_time)) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: [path],
      message: t('Should not be empty'),
    });
  }
};
