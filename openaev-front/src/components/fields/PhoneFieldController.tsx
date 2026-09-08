import { Autocomplete, Box, createFilterOptions, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useController, useFormContext } from 'react-hook-form';

import { type DialCodeOption, dialCodeOption, dialCodeOptions, splitPhoneNumber } from '../../utils/Option';
import { useFormatter } from '../i18n';

interface Props {
  name: string;
  label: string;
  required?: boolean;
  disabled?: boolean;
}

const options = dialCodeOptions();

const filterOptions = createFilterOptions<DialCodeOption>({ stringify: option => `${option.label} ${option.dialCode}` });

const PhoneFieldController: FunctionComponent<Props> = ({
  name,
  label,
  required = false,
  disabled = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { control } = useFormContext();
  const {
    field,
    fieldState: { error },
  } = useController({
    name,
    control,
  });

  const value: string = field.value ?? '';
  const [countryCode, setCountryCode] = useState<string>(() => splitPhoneNumber(value).country?.id ?? '');

  // Keep the dial code in sync when the value is set from outside (form reset, initial values)
  useEffect(() => {
    const detected = splitPhoneNumber(value).country;
    if (detected && detected.id !== countryCode) {
      setCountryCode(detected.id);
    }
  }, [value]);

  const selectedCountry = dialCodeOption(countryCode) ?? null;
  const dialCode = selectedCountry?.dialCode ?? '';
  const nationalNumber = value.startsWith(dialCode) ? value.slice(dialCode.length) : value;

  const handleCountryChange = (option: DialCodeOption | null) => {
    setCountryCode(option?.id ?? '');
    field.onChange(nationalNumber ? `${option?.dialCode ?? ''}${nationalNumber}` : '');
  };

  const handleNumberChange = (nextNationalNumber: string) => {
    field.onChange(nextNationalNumber ? `${dialCode}${nextNationalNumber}` : '');
  };

  return (
    <div>
      <div style={{
        display: 'flex',
        gap: theme.spacing(1),
        alignItems: 'flex-end',
      }}
      >
        <Autocomplete
          sx={{ width: 140 }}
          options={options}
          value={selectedCountry}
          disabled={disabled}
          disableClearable={false}
          filterOptions={filterOptions}
          getOptionLabel={option => option.dialCode}
          isOptionEqualToValue={(option, selected) => option.id === selected.id}
          onChange={(_, option) => handleCountryChange(option)}
          renderOption={(props, option) => (
            <Box component="li" {...props} key={option.id}>
              {`${option.label} (${option.dialCode})`}
            </Box>
          )}
          renderInput={params => (
            <TextField
              {...params}
              label={t('Dial code')}
              variant="standard"
              error={!!error}
            />
          )}
        />
        <TextField
          sx={{ flexGrow: 1 }}
          name={field.name}
          inputRef={field.ref}
          onBlur={field.onBlur}
          value={nationalNumber}
          onChange={event => handleNumberChange(event.target.value)}
          label={required ? `${label}*` : label}
          variant="standard"
          disabled={disabled}
          error={!!error}
        />
      </div>
      {error && (
        <div style={{
          color: theme.palette.error.main,
          ...theme.typography.caption,
          marginTop: theme.spacing(0.5),
        }}
        >
          {error.message}
        </div>
      )}
    </div>
  );
};

export default PhoneFieldController;
