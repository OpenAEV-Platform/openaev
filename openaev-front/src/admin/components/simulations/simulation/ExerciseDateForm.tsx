import { Button } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { FormControlLabel, Stack, Switch } from '@mui/material';
import { type ChangeEvent, type FunctionComponent, useState } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import DateField from '../../../../components/fields/DateField';
import TimeField from '../../../../components/fields/TimeField';
import { useFormatter } from '../../../../components/i18n';
import { type ExerciseUpdateStartDateInput } from '../../../../utils/api-types';
import { minutesInFuture } from '../../../../utils/Time';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<ExerciseUpdateStartDateInput>;
  initialValues?: ExerciseUpdateStartDateInput;
  handleClose: () => void;
}

interface ExerciseStartDateAndTime {
  date: string;
  time: string;
}

// eslint-disable-next-line no-underscore-dangle
const _MS_DELAY_TOO_CLOSE = 1000 * 60 * 2;

const ExerciseDateForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues,
}) => {
  const { t } = useFormatter();

  const defaultFormValues = () => {
    if (initialValues?.exercise_start_date) {
      const date = new Date(initialValues.exercise_start_date);
      return ({
        date: new Date(new Date(date).setHours(0, 0, 0, 0)).toISOString(),
        time: new Date(new Date().setHours(date.getHours(), date.getMinutes(), date.getSeconds(), 0)).toISOString(),
      });
    }
    return ({
      date: new Date(new Date().setUTCHours(0, 0, 0, 0)).toISOString(),
      time: minutesInFuture(5).toDate().toISOString(),
    });
  };

  const [checked, setChecked] = useState(!initialValues?.exercise_start_date);

  const submit = (data: ExerciseStartDateAndTime) => {
    if (checked) {
      onSubmit({ exercise_start_date: '' });
    } else {
      const { date, time } = data;
      const newDate = new Date(date);
      const newTime = new Date(time);
      newDate.setHours(newTime.getHours(), newTime.getMinutes(), newTime.getSeconds());
      onSubmit({ exercise_start_date: newDate.toISOString() });
    }
  };

  const {
    control,
    handleSubmit,
    clearErrors,
    getValues,
    reset,
  } = useForm<ExerciseStartDateAndTime>({
    defaultValues: defaultFormValues(),
    resolver: zodResolver(
      zodImplement<ExerciseStartDateAndTime>().with({
        date: z.string(),
        time: z.string(),
      })
        .refine(
          (data) => {
            if (checked) return true;
            return new Date(new Date().setHours(0, 0, 0, 0)).getTime() !== new Date(data.date).getTime()
              || (new Date().getTime() + _MS_DELAY_TOO_CLOSE) < new Date(data.time).getTime();
          },
          {
            message: t('The time and start date do not match, as the time provided is either too close to the current moment or in the past'),
            path: ['time'],
          },
        )
        .refine(
          (data) => {
            if (checked) return true;
            const time = new Date(data.time);
            return new Date(new Date(data.date).setHours(time.getHours(), time.getMinutes(), time.getSeconds(), 0)).getTime()
              >= new Date(new Date().setHours(0, 0, 0, 0)).getTime();
          },
          {
            message: t('Date should be at least today'),
            path: ['date'],
          },
        ),
    ),
  });

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (event.target.checked) {
      reset(defaultFormValues());
    }
    setChecked(event.target.checked);
  };

  return (
    <form id="exerciseDateForm" onSubmit={handleSubmit(submit)}>
      {/* The switch is one of the fields, not a caption above them: same 16px
          between it and the date as between the date and the time. */}
      <FormControlLabel
        control={<Switch onChange={handleChange} checked={checked} />}
        label={t('Manual launch')}
        sx={{ marginBottom: 2 }}
      />

      <Stack spacing={{ xs: 2 }}>
        <DateField
          control={control}
          name="date"
          label={t('Start date (optional)')}
          disabled={checked}
          minDate={new Date(new Date().setUTCHours(0, 0, 0, 0))}
          onAccept={() => clearErrors('time')}
        />

        <TimeField
          control={control}
          name="time"
          label={t('Scheduling_time')}
          disabled={checked}
          minutesStep={15}
          minTime={new Date(new Date().setUTCHours(0, 0, 0, 0)).getTime() === new Date(getValues('date')).getTime() ? new Date() : undefined}
        />
      </Stack>

      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        {handleClose && (
          <Button type="button" priority="secondary" onClick={handleClose.bind(this)} style={{ marginRight: 10 }}>
            {t('Cancel')}
          </Button>
        )}
        <Button type="submit">
          {t('Save')}
        </Button>
      </div>
    </form>
  );
};
export default ExerciseDateForm;
