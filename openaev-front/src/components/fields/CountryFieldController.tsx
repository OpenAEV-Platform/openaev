import { FlagOutlined } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, TextField } from '@mui/material';
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
  autoCompleteIndicator: { display: 'none' },
}));

interface Props {
  name: string;
  label: string;
}

const CountryFieldController: FunctionComponent<Props> = ({ name, label }) => {
  const { classes } = useStyles();
  const { control } = useFormContext();

  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState: { error } }) => (
        <MuiAutocomplete
          {...field}
          value={countryOptions().find(o => o.id === field.value) || null}
          fullWidth
          multiple={false}
          options={countryOptions()}
          onChange={(_, value) => field.onChange(value?.id || '')}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(option, value) => option.id === value.id}
          renderOption={(props, option) => (
            <Box component="li"{...props} key={option.id}>
              <div className={classes.icon}>
                <FlagOutlined />
              </div>
              <div className={classes.text}>{option.label}</div>
            </Box>
          )}
          renderInput={params => (
            <TextField
              {...params}
              label={label}
              variant="standard"
              error={!!error}
              helperText={error?.message}
            />
          )}
          classes={{ clearIndicator: classes.autoCompleteIndicator }}
        />
      )}
    />
  );
};

export default CountryFieldController;
