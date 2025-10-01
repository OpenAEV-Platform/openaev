import { DateTimePicker } from '@mui/x-date-pickers';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

interface Props {
  name: string;
  label?: string;
}

const useStyles = makeStyles()(theme => ({ root: { '& .MuiOutlinedInput-root': { background: theme.palette.background.code } } }));

const DateTimeFieldController = ({
  name,
  label = '',
}: Props) => {
  const { control } = useFormContext();
  const { classes } = useStyles();

  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <DateTimePicker
          label={label}
          views={['year', 'month', 'day']}
          value={field.value ? new Date(field.value) : null}
          onChange={date => field.onChange(date?.toISOString())}
          className={classes.root}
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
  );
};

export default DateTimeFieldController;
