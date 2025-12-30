import { Autocomplete, Box, Checkbox, TextField, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import { type GroupOption, type Option } from '../../utils/Option';
import { useFormatter } from '../i18n';

type AutocompleteOption = GroupOption | Option;

interface BaseProps {
  label: string;
  options: AutocompleteOption[];
  onInputChange: (search: string) => void;
  required?: boolean;
  error?: boolean;
  className?: string;
  variant?: 'standard' | 'outlined' | 'filled';
  disabled?: boolean;
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

const AutocompleteField: FunctionComponent<Props> = (props) => {
  const {
    label,
    options = [],
    onInputChange,
    required = false,
    error = false,
    className = '',
    variant = 'outlined',
    disabled,
  } = props;

  const multiple = props.multiple === true;
  const value = props.value;
  const { t } = useFormatter();
  const theme = useTheme();

  const selectedOption = useMemo(() => {
    if (!options.length) {
      return multiple ? [] : null;
    }

    if (props.multiple) {
      return options.filter(o => props.value.includes(o.id));
    }

    return options.find(o => o.id === props.value) ?? null;
  }, [props.value, options, props.multiple]);

  const handleValue = (
    newValue: AutocompleteOption | AutocompleteOption[] | null,
  ) => {
    if (props.multiple) {
      const ids = (newValue as AutocompleteOption[]).map(v => v.id);
      props.onChange(ids);
    } else {
      const id = (newValue as AutocompleteOption | null)?.id;
      props.onChange(id);
    }
  };

  return (
    <Autocomplete<AutocompleteOption, boolean>
      disabled={disabled}
      className={className}
      selectOnFocus
      openOnFocus
      autoHighlight
      noOptionsText={t('No available options')}
      multiple={multiple}
      options={options}
      value={selectedOption}
      groupBy={option => ('group' in option ? option.group : '')}
      getOptionLabel={option => option.label ?? ''}
      isOptionEqualToValue={(option, val) => option.id === val.id}
      onInputChange={(_, search, reason) => {
        if (reason === 'input') {
          onInputChange(search);
        }
      }}
      onChange={(_, newValue) => handleValue(newValue)}
      renderInput={params => (
        <TextField
          {...params}
          label={label}
          variant={variant}
          size="small"
          required={required}
          error={error}
        />
      )}
      renderOption={(props, option) => {
        const checked = multiple
          ? value?.includes(option.id)
          : value === option.id;

        return (
          <Tooltip key={option.id} title={option.label}>
            <Box
              component="li"
              {...props}
              sx={{
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                padding: 0,
                margin: 0,
                display: 'flex',
                alignItems: 'center',
              }}
            >
              {multiple && <Checkbox checked={checked} />}

              <Box
                sx={{
                  display: 'inline-block',
                  flexGrow: 1,
                  marginLeft: multiple ? theme.spacing(1) : 0,
                }}
              >
                {option.label}
              </Box>
            </Box>
          </Tooltip>
        );
      }}
    />
  );
};

export default AutocompleteField;
