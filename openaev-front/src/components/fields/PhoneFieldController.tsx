import { PhoneOutlined } from '@mui/icons-material';
import { Autocomplete, Box, createFilterOptions, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useRef, useState } from 'react';
import { useController, useFormContext, useFormState, useWatch } from 'react-hook-form';

import { type DialCodeOption, dialCodeOption, dialCodeOptions, splitPhoneNumber } from '../../utils/Option';
import { useFormatter } from '../i18n';

interface Props {
  name: string;
  label: string;
  required?: boolean;
  disabled?: boolean;
  /** Name of the country field used to prefill the dial code. */
  countryFieldName?: string;
}

const options = dialCodeOptions();

const filterOptions = createFilterOptions<DialCodeOption>({ stringify: option => `${option.label} ${option.dialCode}` });

const PhoneFieldController: FunctionComponent<Props> = ({
  name,
  label,
  required = false,
  disabled = false,
  countryFieldName,
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
  const selectedCountryCode = useWatch({
    control,
    name: countryFieldName ?? '',
    disabled: !countryFieldName,
  }) as string | undefined;

  const { dirtyFields } = useFormState({ control });
  const countryIsDirty = !!countryFieldName && !!dirtyFields[countryFieldName];

  const value: string = field.value ?? '';
  const [countryCode, setCountryCode] = useState<string>(() => splitPhoneNumber(value).country?.id ?? '');
  const valueRef = useRef(value);
  valueRef.current = value;

  // Keep the dial code in sync when the value is set from outside (form reset, initial values).
  // Dial codes are not unique (+1 is shared), so only resync when the prefix really differs.
  useEffect(() => {
    const detected = splitPhoneNumber(value).country;
    if (detected && detected.dialCode !== dialCodeOption(countryCode)?.dialCode) {
      setCountryCode(detected.id);
    }
  }, [value]);

  // Prefill the dial code from the selected country.
  // Without a number only the local state changes, so nothing is persisted.
  // With a number the prefix is rewritten, but only once the user actually changed the country
  // (dirty), never on initial load or after a reset, to keep the saved number untouched.
  useEffect(() => {
    const option = selectedCountryCode ? dialCodeOption(selectedCountryCode) : undefined;
    if (!option) {
      return;
    }
    const { nationalNumber: currentNationalNumber } = splitPhoneNumber(valueRef.current);
    if (!currentNationalNumber) {
      setCountryCode(option.id);
      return;
    }
    if (!countryIsDirty) {
      return;
    }
    setCountryCode(option.id);
    const nextValue = `${option.dialCode}${currentNationalNumber}`;
    if (nextValue !== valueRef.current) {
      field.onChange(nextValue);
    }
  }, [selectedCountryCode, countryIsDirty]);

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
          sx={{ width: 120 }}
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
              <div style={{
                paddingTop: theme.spacing(1),
                display: 'inline-block',
              }}
              >
                <PhoneOutlined />
              </div>
              <div style={{
                display: 'inline-block',
                flexGrow: 1,
                marginLeft: theme.spacing(1),
                whiteSpace: 'nowrap',
              }}
              >
                {`${option.label} (${option.dialCode})`}
              </div>
            </Box>
          )}
          slotProps={{
            popper: {
              placement: 'bottom-start',
              sx: {
                width: 'fit-content !important',
                minWidth: 260,
              },
            },
          }}
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
