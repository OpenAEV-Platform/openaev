import { DateTimePicker } from '@mui/x-date-pickers';
import { Controller, useFormContext } from 'react-hook-form';

interface Props {
  name: string;
  label?: string;
}

const DateTimeFieldController = ({
  name,
  label = '',
}: Props) => {
  const { control } = useFormContext();

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
          slotProps={{
            textField: {
              fullWidth: true,
              error: !!fieldState.error,
              helperText: fieldState.error?.message,
              variant: 'outlined',
            },
          }}
        />
      )}
    />
  );
};

export default DateTimeFieldController;
