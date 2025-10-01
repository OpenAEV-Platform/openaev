import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormControlLabel, InputLabel, MenuItem, Select, Stack, Switch } from '@mui/material';
import { DateTimePicker, TimePicker } from '@mui/x-date-pickers';
import { type FunctionComponent, useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';

import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type ScenarioRecurrenceInput } from '../../../../utils/api-types';
import { generateDailyCron, generateMonthlyCron, generateWeeklyCron, parseCron } from '../../../../utils/Cron';
import { minutesInFuture } from '../../../../utils/Time';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  onSubmit: (cron: string, start: string, end?: string) => void;
  onSelectRecurring: (selectRecurring: string) => void;
  selectRecurring: string;
  initialValues: ScenarioRecurrenceInput;
  open: boolean;
  setOpen: (open: boolean) => void;
}

interface Recurrence {
  startDate: string;
  endDate?: string | null;
  time: string | null;
  onlyWeekday: boolean;
  dayOfWeek?: 1 | 2 | 3 | 4 | 5 | 6 | 7;
  weekOfMonth?: 1 | 2 | 3 | 4 | 5;
}

const defaultFormValues = () => ({
  startDate: new Date(new Date().setUTCHours(0, 0, 0, 0)).toISOString(),
  endDate: null,
  time: minutesInFuture(1).toISOString(),
  onlyWeekday: false,
  dayOfWeek: 1 as Recurrence['dayOfWeek'],
  weekOfMonth: 1 as Recurrence['weekOfMonth'],
});

const ScenarioRecurringFormDialog: FunctionComponent<Props> = ({ onSubmit, selectRecurring, onSelectRecurring, initialValues, open, setOpen }) => {
  const { t } = useFormatter();
  const submit = (data: Recurrence) => {
    const { time } = data as Omit<Recurrence, 'time'> & { time: string };
    // case day
    let cron: string = generateDailyCron(new Date(time).getUTCHours(), new Date(time).getUTCMinutes(), data.onlyWeekday);
    const start = data.startDate;
    let end = data.endDate;
    switch (selectRecurring) {
      case 'noRepeat':
        end = new Date(new Date(data.startDate).setUTCHours(24, 0, 0, 0)).toISOString();
        break;
      case 'weekly':
        cron = generateWeeklyCron(data.dayOfWeek!, new Date(time).getUTCHours(), new Date(time).getUTCMinutes());
        break;
      case 'monthly':
        cron = generateMonthlyCron(data.weekOfMonth!, data.dayOfWeek!, new Date(time).getUTCHours(), new Date(time).getUTCMinutes());
        break;
      default:
        break;
    }
    onSubmit(cron, start, end || '');
  };

  const { handleSubmit, control, reset, getValues, clearErrors } = useForm<Recurrence>({
    defaultValues: defaultFormValues(),
    resolver: zodResolver(
      zodImplement<Recurrence>().with({
        startDate: z.string().min(1, t('Required')),
        endDate: z.string().optional().nullable(),
        onlyWeekday: z.boolean(),
        time: z.string().min(1, t('Required')).nullable(),
        // @ts-expect-error zodImplement cannot handle refine
        dayOfWeek: z.number().optional().refine((value) => {
          if (['weekly', 'monthly'].includes(selectRecurring)) {
            return value !== null;
          }
          return true;
        }, { message: t('Required') }),
        // @ts-expect-error zodImplement cannot handle refine
        weekOfMonth: z.number().optional().refine((value) => {
          if (['monthly'].includes(selectRecurring)) {
            return value !== null;
          }
          return true;
        }, { message: t('Required') }),
      }).refine(
        (data) => {
          if (['noRepeat'].includes(selectRecurring)) {
            if (data.time) {
              return new Date(new Date().setUTCHours(0, 0, 0, 0)).getTime() !== new Date(data.startDate).getTime()
                || new Date(new Date(minutesInFuture(1).toISOString()).setSeconds(0, 0)).getTime() < new Date(data.time).getTime();
            }
          }
          return true;
        },
        {
          message: t('The time and start date do not match, as the time provided is either too close to the current moment or in the past'),
          path: ['time'],
        },
      ).refine(
        (data) => {
          if (['daily', 'weekly', 'monthly'].includes(selectRecurring)) {
            if (data.endDate) {
              return new Date(data.endDate).getTime() > new Date(data.startDate).getTime();
            }
          }

          return true;
        },
        {
          message: t('End date need to be stricly after start date'),
          path: ['endDate'],
        },
      )
        .refine(
          (data) => {
            if (data.startDate) {
              return new Date(data.startDate).getTime() >= new Date(new Date().setUTCHours(0, 0, 0, 0)).getTime();
            }
            return true;
          },
          {
            message: t('Start date should be at least today'),
            path: ['startDate'],
          },
        ),
    ),
  });

  useEffect(() => {
    if (initialValues.scenario_recurrence != null) {
      if (!initialValues.scenario_recurrence || !initialValues.scenario_recurrence_start) {
        reset(defaultFormValues());
      }
      const { w, d, h, m, owd } = parseCron(initialValues.scenario_recurrence);
      reset({
        startDate: initialValues.scenario_recurrence_start,
        endDate: initialValues.scenario_recurrence_end || '',
        onlyWeekday: owd || false,
        time: new Date(new Date().setUTCHours(h, m)).toISOString() || '',
        dayOfWeek: (d || 1) as Recurrence['dayOfWeek'],
        weekOfMonth: (w || 1) as Recurrence['weekOfMonth'],
      });
    }
  }, [initialValues.scenario_recurrence]);

  const handleClose = () => {
    reset(defaultFormValues());
    setOpen(false);
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      TransitionComponent={Transition}
      PaperProps={{ elevation: 1 }}
      maxWidth="xs"
      fullWidth
    >
      <form onSubmit={handleSubmit(submit)}>
        <DialogTitle>{t('Scheduling')}</DialogTitle>
        <DialogContent>
          <Stack spacing={{ xs: 2 }}>
            <Select
              value={selectRecurring}
              label={t('Recurrence')}
              variant="standard"
              onChange={event => onSelectRecurring(event.target.value)}
            >
              <MenuItem value="noRepeat">{t('Does not repeat')}</MenuItem>
              <MenuItem value="daily">{t('Daily')}</MenuItem>
              <MenuItem value="weekly">{t('Weekly')}</MenuItem>
              <MenuItem value="monthly">{t('Monthly')}</MenuItem>
            </Select>
            <Controller
              control={control}
              name="startDate"
              render={({ field, fieldState }) => (
                <DateTimePicker
                  views={['year', 'month', 'day']}
                  value={field.value ? new Date(field.value) : null}
                  minDate={new Date(new Date().setUTCHours(0, 0, 0, 0))}
                  onChange={startDate => field.onChange(startDate?.toISOString())}
                  onAccept={() => {
                    clearErrors('time');
                  }}
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
            {
              ['daily'].includes(selectRecurring)
              && (
                <Controller
                  control={control}
                  name="onlyWeekday"
                  render={({ field }) => (
                    <FormControlLabel
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
              )
            }
            {
              ['monthly'].includes(selectRecurring)
              && (
                <Controller
                  control={control}
                  name="weekOfMonth"
                  render={({ field }) => (
                    <FormControl fullWidth>
                      <InputLabel>{t('Week of month')}</InputLabel>
                      <Select
                        value={field.value}
                        label={t('Week of month')}
                        variant="standard"
                        onChange={field.onChange}
                      >
                        <MenuItem value={1}>{t('First')}</MenuItem>
                        <MenuItem value={2}>{t('Second')}</MenuItem>
                        <MenuItem value={3}>{t('Third')}</MenuItem>
                        <MenuItem value={4}>{t('Fourth')}</MenuItem>
                        <MenuItem value={5}>{t('recurrence_Last')}</MenuItem>
                      </Select>
                    </FormControl>
                  )}
                />
              )
            }
            {
              ['weekly', 'monthly'].includes(selectRecurring)
              && (
                <Controller
                  control={control}
                  name="dayOfWeek"
                  render={({ field }) => (
                    <FormControl fullWidth>
                      <InputLabel>{t('Day of week')}</InputLabel>
                      <Select
                        value={field.value}
                        label={t('Day of week')}
                        variant="standard"
                        onChange={field.onChange}
                      >
                        <MenuItem value={1}>{t('Monday')}</MenuItem>
                        <MenuItem value={2}>{t('Tuesday')}</MenuItem>
                        <MenuItem value={3}>{t('Wednesday')}</MenuItem>
                        <MenuItem value={4}>{t('Thursday')}</MenuItem>
                        <MenuItem value={5}>{t('Friday')}</MenuItem>
                        <MenuItem value={6}>{t('Saturday')}</MenuItem>
                        <MenuItem value={7}>{t('Sunday')}</MenuItem>
                      </Select>
                    </FormControl>
                  )}
                />
              )
            }
            <Controller
              control={control}
              name="time"
              render={({ field, fieldState }) => (
                <TimePicker
                  label={t('Scheduling_time')}
                  openTo="hours"
                  timeSteps={{ minutes: 15 }}
                  skipDisabled
                  thresholdToRenderTimeInASingleColumn={100}
                  closeOnSelect={false}
                  value={field.value ? new Date(field.value) : null}
                  minTime={['noRepeat'].includes(selectRecurring) && new Date(new Date().setUTCHours(0, 0, 0, 0)).getTime() === new Date(getValues('startDate')).getTime() ? new Date() : undefined}
                  onChange={time => (field.onChange(time?.toISOString()))}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      error: !!fieldState.error,
                      helperText: fieldState.error?.message,
                      variant: 'standard',
                    },
                  }}
                />
              )}
            />
            {
              ['daily', 'weekly', 'monthly'].includes(selectRecurring)
              && (
                <Controller
                  control={control}
                  name="endDate"
                  render={({ field, fieldState }) => (
                    <DateTimePicker
                      views={['year', 'month', 'day']}
                      value={field.value ? new Date(field.value) : null}
                      minDate={new Date(new Date().setUTCHours(24, 0, 0, 0))}
                      onChange={(endDate) => {
                        return (endDate ? field.onChange(new Date(new Date(endDate).setUTCHours(0, 0, 0, 0)).toISOString()) : null);
                      }}
                      slotProps={{
                        textField: {
                          fullWidth: true,
                          error: !!fieldState.error,
                          helperText: fieldState.error?.message,
                          variant: 'standard',
                        },
                      }}
                      label={t('End date')}
                    />
                  )}
                />
              )
            }
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>
            {t('Cancel')}
          </Button>
          <Button
            color="secondary"
            type="submit"
          >
            {t('Save')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default ScenarioRecurringFormDialog;
