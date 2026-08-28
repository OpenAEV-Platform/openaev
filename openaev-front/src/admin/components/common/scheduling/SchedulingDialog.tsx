import {
  Select,
  SelectContent,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { UpdateOutlined } from '@mui/icons-material';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  Switch,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { DateTimePicker } from '@mui/x-date-pickers';
import { type FunctionComponent, useEffect, useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';

import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import {
  Cron,
  CronParser,
  generateDailyCronExpression,
  generateHourlyCronExpression,
  generateMonthlyCronExpression,
  generateWeeklyCronExpression,
} from '../../../../utils/period/Cron';
import handle from '../../../../utils/period/Period';
import { minutesInFuture } from '../../../../utils/Time';
import { zodImplement } from '../../../../utils/Zod';
import TimeStepperField, { StepperColumn } from './TimeStepperField';

const MS_PER_DAY = 1000 * 60 * 60 * 24;

type Frequency = 'noRepeat' | 'hourly' | 'daily' | 'weekly' | 'monthly';

const FREQUENCIES: Frequency[] = ['noRepeat', 'hourly', 'daily', 'weekly', 'monthly'];

export interface SchedulingInitialValues {
  recurrence?: string | null;
  recurrenceStart?: string | null;
  recurrenceEnd?: string | null;
}

interface SchedulingFormValues {
  frequency: Frequency;
  startDate: string;
  endDate?: string | null;
  hour: number; // local hour of day (not used for hourly)
  minute: number; // local minute
  interval: number; // hourly interval (hours)
  onlyWeekday: boolean;
  dayOfWeek: number; // ISO 1 (Monday) - 7 (Sunday)
  weekOfMonth: number; // 1-4, 5 = last
}

/** Convert a local wall-clock HH:MM into the UTC parts stored in the cron. */
const localToUtcParts = (hour: number, minute: number) => {
  const d = new Date();
  d.setHours(hour, minute, 0, 0);
  return {
    hour: d.getUTCHours(),
    minute: d.getUTCMinutes(),
  };
};

const buildCronExpression = (data: SchedulingFormValues): string => {
  const utc = localToUtcParts(data.hour, data.minute);
  switch (data.frequency) {
    case 'weekly':
      return generateWeeklyCronExpression(String(data.dayOfWeek), String(utc.hour), String(utc.minute));
    case 'monthly':
      return generateMonthlyCronExpression(String(data.weekOfMonth), String(data.dayOfWeek), String(utc.hour), String(utc.minute));
    case 'hourly':
      // The first argument is the interval in hours, not a time of day.
      return generateHourlyCronExpression(String(data.interval), String(utc.minute), data.onlyWeekday);
    default:
      // 'daily' and 'noRepeat' share the same daily cron; 'noRepeat' is bounded
      // to a single day through the computed end date.
      return generateDailyCronExpression(String(utc.hour), String(utc.minute), data.onlyWeekday);
  }
};

const defaultFormValues = (): SchedulingFormValues => {
  const inTwoMinutes = new Date(minutesInFuture(2).toISOString());
  return {
    frequency: 'daily',
    startDate: new Date(new Date().setUTCHours(0, 0, 0, 0)).toISOString(),
    endDate: null,
    hour: inTwoMinutes.getHours(),
    minute: inTwoMinutes.getMinutes(),
    interval: 1,
    onlyWeekday: false,
    dayOfWeek: 1,
    weekOfMonth: 1,
  };
};

interface Props {
  open: boolean;
  onClose: () => void;
  initialValues: SchedulingInitialValues;
  onSubmit: (cron: Cron, start: string, end?: string) => void;
}

/**
 * Unified scheduling dialog shared by scenarios and atomic testings: segmented
 * frequency control, inline HH:MM stepper (instead of the TimePicker), start /
 * end dates and a live plain-language summary of the resulting schedule.
 */
const SchedulingDialog: FunctionComponent<Props> = ({ open, onClose, initialValues, onSubmit }) => {
  const { t, locale, fld } = useFormatter();

  const existingHandler = useMemo(
    () => (initialValues.recurrence ? handle(initialValues.recurrence) : null),
    [initialValues.recurrence],
  );
  const notEditable = !!existingHandler && !existingHandler.isUiSupported();

  const { handleSubmit, control, reset, watch, setValue, clearErrors, formState } = useForm<SchedulingFormValues>({
    defaultValues: defaultFormValues(),
    resolver: zodResolver(
      zodImplement<SchedulingFormValues>().with({
        frequency: z.enum(['noRepeat', 'hourly', 'daily', 'weekly', 'monthly']),
        startDate: z.string().min(1, t('Required')),
        endDate: z.string().optional().nullable(),
        hour: z.number().int().min(0).max(23),
        minute: z.number().int().min(0).max(59),
        interval: z.number().int().min(1).max(23),
        onlyWeekday: z.boolean(),
        dayOfWeek: z.number().int().min(1).max(7),
        weekOfMonth: z.number().int().min(1).max(5),
      }).refine(
        (data) => {
          // A one-shot occurrence scheduled today must be at least a couple of
          // minutes in the future.
          if (data.frequency === 'noRepeat'
            && new Date(new Date().setUTCHours(0, 0, 0, 0)).getTime() === new Date(data.startDate).getTime()) {
            const occurrence = new Date();
            occurrence.setHours(data.hour, data.minute, 0, 0);
            return occurrence.getTime() > new Date(new Date(minutesInFuture(2).toISOString()).setSeconds(0, 0)).getTime();
          }
          return true;
        },
        {
          message: t('The time and start date do not match, as the time provided is either too close to the current moment or in the past'),
          path: ['hour'],
        },
      ).refine(
        (data) => {
          if (data.frequency !== 'noRepeat' && data.endDate) {
            return new Date(data.endDate).getTime() > new Date(data.startDate).getTime();
          }
          return true;
        },
        {
          message: t('End date need to be strictly after start date'),
          path: ['endDate'],
        },
      ).refine(
        data => new Date(data.startDate).getTime() >= new Date(new Date().setUTCHours(0, 0, 0, 0)).getTime(),
        {
          message: t('Start date should be at least today'),
          path: ['startDate'],
        },
      ),
    ),
  });

  // (Re)initialise the form from the entity's current schedule each time the
  // dialog opens.
  useEffect(() => {
    if (!open) {
      return;
    }
    if (existingHandler instanceof Cron && existingHandler.isUiSupported()) {
      const isHourly = existingHandler.getHours()?.getRecurrence() !== undefined;
      const time = existingHandler.getRecurrenceTime();
      // A one-shot schedule is stored as a daily cron bounded to a single day.
      const isOnce = !!initialValues.recurrenceStart && !!initialValues.recurrenceEnd
        && new Date(initialValues.recurrenceEnd).getTime() - new Date(initialValues.recurrenceStart).getTime() <= MS_PER_DAY
        && existingHandler.getRecurrenceMagnitude() === 'daily';
      const monthlyRecurrence = existingHandler.getMonthlyRecurrence();
      const defaults = defaultFormValues();
      reset({
        ...defaults,
        frequency: isOnce ? 'noRepeat' : (existingHandler.getRecurrenceMagnitude() as Frequency),
        startDate: initialValues.recurrenceStart || defaults.startDate,
        endDate: isOnce ? null : (initialValues.recurrenceEnd || null),
        hour: time.hour ?? defaults.hour,
        minute: time.minute ?? defaults.minute,
        interval: isHourly ? Number(existingHandler.getHours()?.getRecurrence()) || 1 : 1,
        onlyWeekday: !!existingHandler.isOnlyOnWeekdays(),
        dayOfWeek: Number(existingHandler.getWeeklyRecurrence()) || 1,
        weekOfMonth: monthlyRecurrence === 'L' ? 5 : Number(monthlyRecurrence) || 1,
      });
    } else {
      reset(defaultFormValues());
    }
  }, [open, existingHandler, initialValues.recurrenceStart, initialValues.recurrenceEnd]);

  const values = watch();
  const frequency = values.frequency;

  // Live plain-language summary of the schedule being configured.
  const summary = useMemo(() => {
    try {
      if (frequency === 'noRepeat') {
        const time = new Date();
        time.setHours(values.hour, values.minute, 0, 0);
        return t('Runs once on {date} at {time}', {
          date: fld(values.startDate),
          time: time.toLocaleTimeString(locale, {
            hour: '2-digit',
            minute: '2-digit',
          }),
        });
      }
      let sentence = CronParser.parse(buildCronExpression(values)).toHumanReadableString(locale);
      if (values.endDate) {
        sentence += ` ${t('recurrence_from')} ${fld(values.startDate)} ${t('recurrence_to')} ${fld(values.endDate)}`;
      } else {
        sentence += ` ${t('recurrence_starting_from')} ${fld(values.startDate)}`;
      }
      return sentence;
    } catch {
      return null;
    }
  }, [values.frequency, values.startDate, values.endDate, values.hour, values.minute, values.interval, values.onlyWeekday, values.dayOfWeek, values.weekOfMonth, locale]);

  const submit = (data: SchedulingFormValues) => {
    const cron = buildCronExpression(data);
    const start = data.startDate;
    // A one-shot schedule is bounded to the day after its start so the
    // backend self-clears it once fired.
    const end = data.frequency === 'noRepeat'
      ? new Date(new Date(data.startDate).setUTCHours(24, 0, 0, 0)).toISOString()
      : data.endDate || undefined;
    onSubmit(CronParser.parse(cron), start, end);
  };

  const handleClose = () => {
    reset(defaultFormValues());
    onClose();
  };

  const frequencyLabels: Record<Frequency, string> = {
    noRepeat: t('Once'),
    hourly: t('Hourly'),
    daily: t('Daily'),
    weekly: t('Weekly'),
    monthly: t('Monthly'),
  };

  const timeError = formState.errors.hour?.message || formState.errors.minute?.message;

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      TransitionComponent={Transition}
      PaperProps={{ elevation: 1 }}
      maxWidth="sm"
      fullWidth
    >
      {notEditable && (
        <DialogContent>
          <Alert severity="warning">
            {t(
              'The currently configured scheduling cannot be rendered in this view and cannot be modified. '
              + 'Please cancel the current scheduling and set up a new one from scratch.',
            )}
          </Alert>
        </DialogContent>
      )}
      {!notEditable && (
        <form onSubmit={handleSubmit(submit)}>
          <DialogTitle>{t('Scheduling')}</DialogTitle>
          <DialogContent>
            <Stack spacing={2.5}>
              {/* Segmented frequency selector. */}
              <Controller
                control={control}
                name="frequency"
                render={({ field }) => (
                  <ToggleButtonGroup
                    value={field.value}
                    exclusive
                    fullWidth
                    size="small"
                    onChange={(_, next: Frequency | null) => {
                      if (next) {
                        field.onChange(next);
                        clearErrors();
                      }
                    }}
                  >
                    {FREQUENCIES.map(value => (
                      <ToggleButton key={value} value={value} sx={{ textTransform: 'none' }}>
                        {frequencyLabels[value]}
                      </ToggleButton>
                    ))}
                  </ToggleButtonGroup>
                )}
              />
              {/* Time of day (or interval + minute for hourly). */}
              <Box sx={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 4,
                flexWrap: 'wrap',
              }}
              >
                {frequency === 'hourly'
                  ? (
                      <>
                        <Box>
                          <Typography variant="caption" component="div" sx={{ color: 'text.secondary' }}>
                            {t('Interval (hours)')}
                          </Typography>
                          <Box sx={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            border: theme => `1px solid ${theme.palette.divider}`,
                            borderRadius: 1,
                            paddingInline: 1.5,
                            paddingBlock: 0.25,
                            marginTop: 0.5,
                          }}
                          >
                            <StepperColumn
                              value={values.interval}
                              onChange={next => setValue('interval', Math.max(1, next), { shouldValidate: true })}
                              min={1}
                              max={23}
                              ariaLabel={t('Interval (hours)')}
                            />
                          </Box>
                        </Box>
                        <Box>
                          <Typography variant="caption" component="div" sx={{ color: 'text.secondary' }}>
                            {t('At minute')}
                          </Typography>
                          <Box sx={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            border: theme => `1px solid ${theme.palette.divider}`,
                            borderRadius: 1,
                            paddingInline: 1.5,
                            paddingBlock: 0.25,
                            marginTop: 0.5,
                          }}
                          >
                            <StepperColumn
                              value={values.minute}
                              onChange={next => setValue('minute', next, { shouldValidate: true })}
                              max={59}
                              step={5}
                              ariaLabel={t('At minute')}
                            />
                          </Box>
                        </Box>
                      </>
                    )
                  : (
                      <TimeStepperField
                        label={t('Scheduling_time')}
                        hour={values.hour}
                        minute={values.minute}
                        onChangeHour={next => setValue('hour', next, { shouldValidate: true })}
                        onChangeMinute={next => setValue('minute', next, { shouldValidate: true })}
                        error={timeError}
                        hourLabel={t('Hours')}
                        minuteLabel={t('Minutes')}
                      />
                    )}
                {['weekly', 'monthly'].includes(frequency) && (
                  <Stack
                    spacing={2}
                    sx={{
                      flex: 1,
                      minWidth: 200,
                    }}
                  >
                    {frequency === 'monthly' && (
                      <Controller
                        control={control}
                        name="weekOfMonth"
                        render={({ field }) => (
                          <Select
                            value={String(field.value ?? '')}
                            onValueChange={next => (field.onChange)(Number(next))}
                          >
                            <SelectLabel>{t('Week of month')}</SelectLabel>
                            <SelectTrigger>
                              <SelectValue placeholder={t('Week of month')} />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="1">{t('First')}</SelectItem>
                              <SelectItem value="2">{t('Second')}</SelectItem>
                              <SelectItem value="3">{t('Third')}</SelectItem>
                              <SelectItem value="4">{t('Fourth')}</SelectItem>
                              <SelectItem value="5">{t('recurrence_Last')}</SelectItem>
                            </SelectContent>
                          </Select>
                        )}
                      />
                    )}
                    <Controller
                      control={control}
                      name="dayOfWeek"
                      render={({ field }) => (
                        <Select
                          value={String(field.value ?? '')}
                          onValueChange={next => (field.onChange)(Number(next))}
                        >
                          <SelectLabel>{t('Day of week')}</SelectLabel>
                          <SelectTrigger>
                            <SelectValue placeholder={t('Day of week')} />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="1">{t('Monday')}</SelectItem>
                            <SelectItem value="2">{t('Tuesday')}</SelectItem>
                            <SelectItem value="3">{t('Wednesday')}</SelectItem>
                            <SelectItem value="4">{t('Thursday')}</SelectItem>
                            <SelectItem value="5">{t('Friday')}</SelectItem>
                            <SelectItem value="6">{t('Saturday')}</SelectItem>
                            <SelectItem value="7">{t('Sunday')}</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                    />
                  </Stack>
                )}
                {['hourly', 'daily'].includes(frequency) && (
                  <Controller
                    control={control}
                    name="onlyWeekday"
                    render={({ field }) => (
                      <FormControlLabel
                        sx={{ marginTop: 2 }}
                        control={(
                          <Switch
                            checked={field.value}
                            onChange={field.onChange}
                          />
                        )}
                        label={t('Only weekday')}
                      />
                    )}
                  />
                )}
              </Box>
              {/* Start / end window. */}
              <Box sx={{
                display: 'flex',
                gap: 2,
              }}
              >
                <Controller
                  control={control}
                  name="startDate"
                  render={({ field, fieldState }) => (
                    <DateTimePicker
                      views={['year', 'month', 'day']}
                      value={field.value ? new Date(field.value) : null}
                      minDate={new Date(new Date().setUTCHours(0, 0, 0, 0))}
                      onChange={startDate => field.onChange(startDate?.toISOString())}
                      onAccept={() => clearErrors(['hour', 'minute'])}
                      slotProps={{
                        textField: {
                          fullWidth: true,
                          error: !!fieldState.error,
                          helperText: fieldState.error?.message,
                          variant: 'standard',
                        },
                      }}
                      label={t('Start date')}
                    />
                  )}
                />
                {frequency !== 'noRepeat' && (
                  <Controller
                    control={control}
                    name="endDate"
                    render={({ field, fieldState }) => (
                      <DateTimePicker
                        views={['year', 'month', 'day']}
                        value={field.value ? new Date(field.value) : null}
                        minDate={new Date(new Date().setUTCHours(24, 0, 0, 0))}
                        onChange={endDate => field.onChange(endDate ? new Date(new Date(endDate).setUTCHours(0, 0, 0, 0)).toISOString() : null)}
                        slotProps={{
                          textField: {
                            fullWidth: true,
                            error: !!fieldState.error,
                            helperText: fieldState.error?.message,
                            variant: 'standard',
                          },
                        }}
                        label={t('End date (optional)')}
                      />
                    )}
                  />
                )}
              </Box>
              {/* Live plain-language summary. */}
              {summary && (
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  padding: 1.5,
                  borderRadius: 1,
                  backgroundColor: theme => theme.palette.action.hover,
                }}
                >
                  <UpdateOutlined fontSize="small" color="primary" />
                  <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                    {summary}
                  </Typography>
                </Box>
              )}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button variant="outlined" color="primary" onClick={handleClose}>
              {t('Cancel')}
            </Button>
            <Button variant="contained" color="primary" type="submit">
              {t('Save')}
            </Button>
          </DialogActions>
        </form>
      )}
    </Dialog>
  );
};

export default SchedulingDialog;
