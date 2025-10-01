import { Checkbox, FormControlLabel } from '@mui/material';
import { type CSSProperties } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

interface Props {
  name: string;
  label: string;
  style?: CSSProperties;
}

const CheckboxFieldController = ({ name, label, style }: Props) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <FormControlLabel style={style} label={label} control={<Checkbox {...field} checked={field.value ?? false} />} />
      )}
    />
  );
};

export default CheckboxFieldController;
