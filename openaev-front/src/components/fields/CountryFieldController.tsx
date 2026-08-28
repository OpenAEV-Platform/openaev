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
import { FlagOutlined } from '@mui/icons-material';
import type { FunctionComponent } from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { countryOptions } from '../../utils/Option';

const useStyles = makeStyles()(theme => ({
  icon: {
    paddingTop: theme.spacing(1),
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: theme.spacing(1),
  },
}));

interface Props {
  name: string;
  label: string;
}

type CountryOption = ReturnType<typeof countryOptions>[number];

const CountryFieldController: FunctionComponent<Props> = ({ name, label }) => {
  const { classes } = useStyles();
  const { control } = useFormContext();

  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState: { error } }) => (
        <Combobox<CountryOption>
          options={countryOptions()}
          value={countryOptions().find(o => o.id === field.value) ?? null}
          onValueChange={value => field.onChange((value as CountryOption | null)?.id ?? '')}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(option, value) => option.id === value.id}
          // The MUI field hid its clear control via a `classes` override.
          clearable={false}
          error={!!error}
          renderOption={option => (
            <>
              <div className={classes.icon}>
                <FlagOutlined />
              </div>
              <div className={classes.text}>{option.label}</div>
            </>
          )}
        >
          <ComboboxLabel>{label}</ComboboxLabel>
          <ComboboxField>
            <ComboboxInput onBlur={field.onBlur} name={field.name} ref={field.ref} />
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

export default CountryFieldController;
