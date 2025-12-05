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
      render={({ field: { onChange, value }, fieldState: { error } }) => (
        <>
          <Autocomplete
            size="small"
            multiple
            options={filteredDomains}
            getOptionLabel={option => option.domain_name}
            isOptionEqualToValue={(option, val) => option.domain_id === val.domain_id}
            disableClearable={false}
            openOnFocus
            autoHighlight
            noOptionsText="No available options"
            value={Array.isArray(value) ? value : []}
            onChange={(_event, selectedOptions) => {
              onChange(selectedOptions);
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
        </>
      )}
    />
  );
};

export default DomainFieldController;
