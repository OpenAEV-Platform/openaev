import { Autocomplete, TextField } from '@mui/material';
import { Controller, useFormContext } from 'react-hook-form';

import { useFormatter } from '../i18n';

interface Props {
  name: string;
  label: string;
  defaultValue?: string;
  required?: boolean;
  disabled?: boolean;
}

const SeparatorFieldController = ({ name, label, disabled, defaultValue, required = false }: Props) => {
  const { control } = useFormContext();
  const { t } = useFormatter();
  const separatorItems = [
    {
      value: ',',
      label: t('Comma'),
    },
    {
      value: ';',
      label: t('Semicolon'),
    },
    {
      value: '|',
      label: t('Pipe'),
    },
    {
      value: ' ',
      label: t('Space'),
    },
  ];

  return (
    <Controller
      name={name}
      control={control}
      defaultValue={defaultValue ?? ''}
      render={({ field, fieldState: { error } }) => (
        <Autocomplete
          size="medium"
          options={separatorItems}
          freeSolo
          disabled={disabled}
          renderInput={
            params => (
              <TextField
                {...params}
                label={t(label)}
                fullWidth
                required={required}
                error={!!error}
                helperText={error?.message}
                onChange={(event) => {
                  field.onChange(event.target.value);
                }}
              />
            )
          }
          value={separatorItems.find(item => item.value === field.value)?.label ?? field.value}
          onChange={(_event, platform) => {
            field.onChange(platform?.value || '');
          }}
        />
      )}
    />
  );
};

export default SeparatorFieldController;
