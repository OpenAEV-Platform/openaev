import { Autocomplete as MuiAutocomplete, Box, Chip, TextField } from '@mui/material';
import { type FunctionComponent, useMemo } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type Option } from '../../../../utils/Option';

/**
 * Search-driven autocomplete matching the platform's standard form fields
 * (full-size `variant="standard"`, like PersonFieldController) - the shared
 * AutocompleteField is outlined + small and visually off inside drawers.
 */
interface BaseProps {
  label: string;
  options: Option[];
  onInputChange: (search: string) => void;
  required?: boolean;
  error?: boolean;
  helperText?: string;
}

interface SingleProps extends BaseProps {
  multiple?: false;
  value: string | undefined;
  onChange: (value: string | undefined) => void;
}

interface MultipleProps extends BaseProps {
  multiple: true;
  value: string[];
  onChange: (value: string[]) => void;
}

type Props = SingleProps | MultipleProps;

const ReportingAutocompleteField: FunctionComponent<Props> = (props) => {
  const { label, options, onInputChange, required = false, error = false, helperText } = props;
  const { t } = useFormatter();

  const selected = useMemo(() => {
    if (props.multiple) {
      // Selected ids missing from the current (narrowed) search results keep a
      // stub option so their chip never disappears.
      return props.value.map(id => options.find(option => option.id === id) ?? {
        id,
        label: id,
      });
    }
    return options.find(option => option.id === props.value) ?? null;
  }, [props.multiple, props.value, options]);

  return (
    <MuiAutocomplete<Option, boolean>
      fullWidth
      openOnFocus
      autoHighlight
      multiple={props.multiple === true}
      noOptionsText={t('No available options')}
      options={options}
      value={selected}
      getOptionLabel={option => option.label ?? ''}
      isOptionEqualToValue={(option, value) => option.id === value.id}
      onInputChange={(_, search, reason) => {
        if (reason === 'input') {
          onInputChange(search);
        }
      }}
      onChange={(_, newValue) => {
        if (props.multiple) {
          props.onChange(((newValue ?? []) as Option[]).map(option => option.id));
        } else {
          props.onChange((newValue as Option | null)?.id);
        }
      }}
      renderOption={(liProps, option) => (
        <Box component="li" {...liProps} key={option.id}>
          {option.label}
        </Box>
      )}
      renderTags={(tagValue, getTagProps) => tagValue.map((option, index) => (
        <Chip
          label={option.label}
          {...getTagProps({ index })}
          key={option.id}
          size="small"
          style={{ borderRadius: 4 }}
        />
      ))}
      renderInput={params => (
        <TextField
          {...params}
          label={label}
          variant="standard"
          required={required}
          error={error}
          helperText={helperText}
        />
      )}
    />
  );
};

export default ReportingAutocompleteField;
