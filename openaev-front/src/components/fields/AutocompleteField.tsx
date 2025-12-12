import { Autocomplete, Box, Checkbox, TextField, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { type GroupOption, type Option } from '../../utils/Option';
import { useFormatter } from '../i18n';

type AutocompleteOption = GroupOption | Option;

interface Props {
  label: string;
  value: string | string[] | undefined;
  options: AutocompleteOption[];
  onInputChange: (search: string) => void;
  onChange: (value: string | string[] | undefined) => void;
  multiple?: boolean;
  required?: boolean;
  error?: boolean;
  className?: string;
  variant?: 'standard' | 'outlined' | 'filled';
  disabled?: boolean;
}

const AutocompleteField: FunctionComponent<Props> = ({
  label,
  value,
  options = [],
  onInputChange,
  onChange,
  multiple = false,
  required = false,
  error = false,
  className = '',
  variant = 'outlined',
  disabled,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [currentValue, setCurrentValue] = useState(value);

  useEffect(() => {
    setCurrentValue(value);
  }, [value]);

  const selectedOption = useMemo(() => {
    if (!options.length) return multiple ? [] : null;

    if (multiple) {
      if (!Array.isArray(currentValue)) return [];
      return options.filter(o => currentValue.includes(o.id));
    }

    if (!multiple && typeof currentValue === 'string') {
      return options.find(o => o.id === currentValue) || null;
    }

    return multiple ? [] : null;
  }, [currentValue, options, multiple]);

  const handleValue = (newValue: unknown) => {
    if (multiple) {
      const ids = ((newValue ?? []) as AutocompleteOption[]).map(v => v.id);
      setCurrentValue(ids);
      onChange(ids);
    } else {
      const id = (newValue as AutocompleteOption | null | undefined)?.id;
      setCurrentValue(id);
      onChange(id);
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
      groupBy={(option: AutocompleteOption) =>
        'group' in option ? option.group : ''}
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
        delete props.key;

        const checked = multiple
          ? Array.isArray(currentValue) && currentValue.includes(option.id)
          : currentValue === option.id;

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
