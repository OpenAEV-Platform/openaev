import { FormControl, FormHelperText, InputLabel, ListItemText, MenuItem, Select } from '@mui/material';
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
  disabled,
}: DomainFieldControllerProps) => {
  const { control } = useFormContext();

  const filteredDomains = domains.filter(d => d.domain_name !== 'Unclassified');

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => {
        const { value, onChange, ...restField } = field;
        const selectedIds = Array.isArray(value)
          ? value.map(d => d.domain_id)
          : [];

        return (
          <FormControl fullWidth error={!!error}>
            <InputLabel id={`select-label-${name}`} error={!!error}>
              {`${label}${required ? ' *' : ''}`}
            </InputLabel>

            <Select
              {...restField}
              value={selectedIds}
              multiple
              disabled={disabled}
              onChange={(e) => {
                const ids = e.target.value as string[];
                const selectedDomains = filteredDomains.filter(d => ids.includes(d.domain_id));
                onChange(selectedDomains);
              }}
              renderValue={selectedIds =>
                filteredDomains
                  .filter(domain => selectedIds.includes(domain.domain_id))
                  .map(domain => domain.domain_name)
                  .join(', ')}
            >
              {filteredDomains.map(domain => (
                <MenuItem key={domain.domain_id} value={domain.domain_id}>
                  <ListItemText primary={domain.domain_name} />
                </MenuItem>
              ))}
            </Select>

            {error && <FormHelperText>{error.message}</FormHelperText>}
          </FormControl>
        );
      }}
    />
  );
};

export default DomainFieldController;
