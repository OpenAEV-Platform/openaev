import { FormControlLabel, Switch } from '@mui/material';
import type React from 'react';
import { Controller, useFormContext } from 'react-hook-form';

interface Props {
  label: React.ReactNode;
  name: string;
  size?: 'small' | 'medium';
  required?: boolean;
  disabled?: boolean;
}

const SwitchFieldController = ({ name, label, size = 'medium', required = false, disabled = false }: Props) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <FormControlLabel
          control={<Switch {...field} size={size} checked={!!field?.value} disabled={disabled} required={required} />}
          label={label}
        />
      )}
    />
  );
};

export default SwitchFieldController;
