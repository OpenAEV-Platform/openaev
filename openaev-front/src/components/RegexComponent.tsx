import { Autocomplete, TextField } from '@mui/material';
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

  const inputLabelProps = required ? { required: true } : {};

  return (
    <Autocomplete
      selectOnFocus
      openOnFocus
      autoHighlight
      noOptionsText={t('No available options')}
      renderInput={
        params => (
          <TextField
            {...params}
            label={t(label)}
            variant="outlined"
            size="small"
            InputLabelProps={inputLabelProps}
            error={!!error}
            helperText={error?.message}
          />
        )
      }
      options={regexOptions}
      value={regexOptions.find(r => r === value) ?? null}
      onChange={(_event, newValue) => {
        setValue(newValue);
        onChange(newValue);
      }}
    />
  );
};

export default RegexComponent;
