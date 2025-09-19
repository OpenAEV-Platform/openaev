import { Autocomplete, Box, Checkbox, TextField, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import type { GroupOption, Option } from '../../utils/Option';
import { useFormatter } from '../i18n';

interface Props {
  label: string;
  value: string | undefined;
  options: GroupOption[] | Option[];
  onInputChange: (search: string) => void;
  onChange: (value: string | undefined) => void;
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
  required = false,
  error = false,
  className = '',
  variant = 'outlined',
  disabled,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [currentValue, setCurrentValue] = useState<string | undefined>(value);

  useEffect(() => {
    setCurrentValue(value);
  }, [value]);

  const selectedOption = useMemo(() => {
    if (!currentValue || options.length === 0) return null;
    return options.find(o => o.id === currentValue) || null;
  }, [currentValue, options]);

  const handleValue = (optionId: string | undefined) => {
    const newValue = currentValue === optionId ? undefined : optionId;
    setCurrentValue(newValue);
    onChange(newValue);
  };

  return (
    <Autocomplete
      disabled={disabled}
      selectOnFocus
      className={className}
      groupBy={(option: GroupOption | Option) => 'group' in option ? option.group : ''}
      openOnFocus
      autoHighlight
      noOptionsText={t('No available options')}
      options={options}
      getOptionLabel={option => option.label ?? ''}
      value={selectedOption}
      isOptionEqualToValue={(option, value) => option.id === value.id}
      onInputChange={(_, search, reason) => {
        if (reason === 'input') {
          onInputChange(search);
        }
      }}
      onChange={(_, newValue) => {
        const newId = newValue?.id ?? undefined;
        handleValue(newId);
      }}
      renderInput={paramsInput => (
        <TextField
          {...paramsInput}
          error={error}
          label={label}
          variant={variant}
          size="small"
          required={required}
        />
      )}
      renderOption={(props, option) => {
        delete props.key;
        const checked = currentValue === option.id;
        return (
          <Tooltip key={option.id} title={option.label}>
            <Box
              component="li"
              {...props}
              style={{
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                padding: 0,
                margin: 0,
              }}
            >
              <Checkbox checked={checked} />
              <div style={{
                display: 'inline-block',
                flexGrow: 1,
                marginLeft: theme.spacing(1),
              }}
              >
                {option.label}
              </div>
            </Box>
          </Tooltip>
        );
      }}
    />
  );
};

export default AutocompleteField;
