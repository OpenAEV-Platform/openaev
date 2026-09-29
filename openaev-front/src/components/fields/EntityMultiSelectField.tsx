import { Autocomplete as MuiAutocomplete, Box, TextField } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type ReactNode } from 'react';
import { type GlobalError } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type Option } from '../../utils/Option';

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
  autoCompleteIndicator: { display: 'none' },
}));

interface Props {
  label: string;
  options: Option[];
  fieldValue: string[];
  fieldOnChange: (values: string[]) => void;
  icon?: ReactNode;
  error?: GlobalError;
  placeholder?: string;
  style?: CSSProperties;
  disabled?: boolean;
  required?: boolean;
}

/**
 * Presentational multi-selector over a list of entities identified by their id.
 * Holds no data-fetching and no form binding: callers provide the options and
 * the value/onChange pair, exactly like TagField.
 */
const EntityMultiSelectField: FunctionComponent<Props> = ({
  label,
  options,
  fieldValue,
  fieldOnChange,
  icon,
  error,
  placeholder = '',
  style,
  disabled = false,
  required = false,
}) => {
  const { classes } = useStyles();

  return (
    <MuiAutocomplete
      multiple
      fullWidth
      size="small"
      selectOnFocus
      autoHighlight
      clearOnBlur={false}
      clearOnEscape={false}
      disableClearable
      disabled={disabled}
      slotProps={{ paper: { elevation: 2 } }}
      options={options}
      value={options.filter(option => fieldValue.includes(option.id))}
      onChange={(_, newValue) => fieldOnChange(newValue.map(option => option.id))}
      getOptionLabel={option => option.label}
      isOptionEqualToValue={(option, value) => option.id === value.id}
      renderOption={(props, option) => (
        <Box component="li" {...props} key={option.id}>
          {icon && <div className={classes.icon}>{icon}</div>}
          <div className={classes.text}>{option.label}</div>
        </Box>
      )}
      renderInput={params => (
        <TextField
          {...params}
          label={label}
          variant="standard"
          placeholder={placeholder}
          fullWidth
          required={required}
          error={!!error}
          helperText={error?.message}
        />
      )}
      classes={{ clearIndicator: classes.autoCompleteIndicator }}
      style={style}
    />
  );
};

export default EntityMultiSelectField;
