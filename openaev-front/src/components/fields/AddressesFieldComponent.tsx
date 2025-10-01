import { FormHelperText, TextField } from '@mui/material';
import type { CSSProperties, FormEventHandler } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { useFormatter } from '../i18n';

interface Props {
  name: string;
  label: string;
  style?: CSSProperties;
  helperText: string;
  disabled?: boolean;
  required?: boolean;
}

const AddressesFieldComponent = ({ name, label, style = {}, disabled = false, required = false, helperText }: Props) => {
  const { control } = useFormContext();
  const { t } = useFormatter();

  return (
    <Controller
      control={control}
      name={name}
      render={({ field: { onChange, onBlur, value }, fieldState: { error } }) => {
        const value2 = value?.reduce((accumulator: string, current: string) => (accumulator === '' ? current : `${accumulator}\n${current}`), '');
        const onChange2: FormEventHandler<HTMLTextAreaElement | HTMLInputElement> = (event) => {
          if (event.currentTarget.value === '') {
            onChange([]);
          } else {
            onChange(event.currentTarget.value.split('\n'));
          }
        };
        return (
          <>
            <TextField
              variant="standard"
              fullWidth
              multiline
              rows={3}
              label={label}
              style={style}
              error={!!error}
              disabled={disabled}
              helperText={error ? error.message : null}
              onChange={onChange2}
              onBlur={onBlur}
              value={value2}
              required={required}
            />
            <FormHelperText>{t(helperText)}</FormHelperText>
          </>
        );
      }}
    />
  );
};

export default AddressesFieldComponent;
