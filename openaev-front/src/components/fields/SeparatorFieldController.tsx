import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
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
        <Combobox<{
          value: string;
          label: string;
        }>
          allowCustomValue
          createValueFromInput={input => ({
            value: input,
            label: input,
          })}
          options={separatorItems}
          value={separatorItems.find(item => item.value === field.value)
            ?? (field.value
              ? {
                  value: field.value,
                  label: field.value,
                }
              : null)}
          onValueChange={(next) => {
            field.onChange((next as { value: string } | null)?.value ?? '');
          }}
          getOptionLabel={item => item.label}
          isOptionEqualToValue={(a, b) => a.value === b.value}
          disabled={disabled}
          required={required}
          error={!!error}
        >
          <ComboboxLabel>{t(label)}</ComboboxLabel>
          <ComboboxField>
            <ComboboxInput />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
          {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
        </Combobox>
      )}
    />
  );
};

export default SeparatorFieldController;
