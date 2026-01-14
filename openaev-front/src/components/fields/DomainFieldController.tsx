import { Autocomplete, Box, TextField } from '@mui/material';
import type { CSSProperties } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { type Domain } from '../../utils/api-types';

interface DomainFieldControllerProps {
  name: string;
  label: string;
  domains: Domain[];
  style?: CSSProperties;
  required?: boolean;
  disabled?: boolean;
}

const DomainFieldController = ({
  name,
  label,
  domains,
  required,
}: DomainFieldControllerProps) => {
  const { control } = useFormContext();

  const filteredDomains = domains.filter(d => d.domain_name !== 'To classify');

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => {
        const currentValues = Array.isArray(value) ? value : [];

        return (
          <Autocomplete
            size="small"
            multiple
            options={filteredDomains}
            getOptionLabel={option => option.domain_name}
            isOptionEqualToValue={(option, val) => option.domain_id === val.domain_id}
            value={currentValues}
            onChange={(_event, selectedOptions) => {
              const finalOptions = selectedOptions.length > 1
                ? selectedOptions.filter(d => d.domain_name !== 'To classify')
                : selectedOptions;

              onChange(finalOptions);
            }}
            renderInput={params => (
              <TextField
                {...params}
                label={`${label}${required ? ' *' : ''}`}
                variant="standard"
                size="small"
                fullWidth
                error={!!error}
                helperText={error ? error.message : null}
              />
            )}
            renderOption={(props, option) => (
              <Box component="li" {...props} key={option.domain_id}>
                {option.domain_name}
              </Box>
            )}
          />
        );
      }}
    />
  );
};

export default DomainFieldController;
