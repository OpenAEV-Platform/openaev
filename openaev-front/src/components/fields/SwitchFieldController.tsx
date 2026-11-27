import { Switch } from '@filigran/design-system';
import type React from 'react';
import { Controller, useFormContext } from 'react-hook-form';

interface Props {
  label: React.ReactNode;
  name: string;
  required?: boolean;
  disabled?: boolean;
}

const SwitchFieldController = ({ name, label, required = false, disabled = false }: Props) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <Switch
          ref={field.ref}
          name={field.name}
          onBlur={field.onBlur}
          checked={!!field?.value}
          onCheckedChange={checked => field.onChange(checked === true)}
          disabled={disabled}
          required={required}
          label={label}
        />
      )}
    />
  );
};

export default SwitchFieldController;
