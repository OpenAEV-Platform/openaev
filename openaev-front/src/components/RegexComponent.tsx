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
import { type FunctionComponent, useState } from 'react';
import { type FieldError } from 'react-hook-form';

import alphabet from '../admin/components/settings/data_ingestion/AttributeUtils';
import { useFormatter } from './i18n';

interface Props {
  label: string;
  fieldValue: string | null | undefined;
  onChange: (data: string | null) => void;
  required?: boolean;
  error: FieldError | undefined;
}

const RegexComponent: FunctionComponent<Props> = ({
  label,
  fieldValue,
  onChange,
  required,
  error,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const regexOptions = alphabet(26);
  const [value, setValue] = useState<string | null | undefined>(fieldValue ?? '');

  return (
    <Combobox<string>
      openOnFocus
      required={required}
      error={!!error}
      options={regexOptions}
      value={regexOptions.find(r => r === value) ?? null}
      onValueChange={(newValue) => {
        setValue(newValue as string | null);
        onChange(newValue as string | null);
      }}
    >
      <ComboboxLabel>{t(label)}</ComboboxLabel>
      <ComboboxField>
        <ComboboxInput />
        <ComboboxControls>
          <ComboboxTrigger />
        </ComboboxControls>
      </ComboboxField>
      <ComboboxContent emptyMessage={t('No available options')} />
      {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
    </Combobox>
  );
};

export default RegexComponent;
