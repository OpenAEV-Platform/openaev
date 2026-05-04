import { HomeWorkOutlined } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, TextField } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { searchTenants } from '../../actions/platform/tenants/tenant-action';
import type { TenantOutput } from '../../utils/api-types';
import type { Option } from '../../utils/Option';

interface Props {
  name: string;
  label: string;
  disabled?: boolean;
}

const TenantFieldController: FunctionComponent<Props> = ({ name, label, disabled = false }) => {
  const { control } = useFormContext();
  const [options, setOptions] = useState<Option[]>([]);

  const fetchTenants = useCallback(async () => {
    const result = await searchTenants({
      size: 100,
      page: 0,
    });
    const tenants: TenantOutput[] = result?.data?.content ?? [];
    setOptions(tenants.map(tenant => ({
      id: tenant.tenant_id,
      label: tenant.tenant_name,
    })));
  }, []);

  useEffect(() => {
    fetchTenants();
  }, [fetchTenants]);

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => (
        <MuiAutocomplete
          multiple
          fullWidth
          disabled={disabled}
          options={options}
          value={options.filter(o => (value as string[] ?? []).includes(o.id))}
          onChange={(_, newValue) => onChange(newValue.map(v => v.id))}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(option, val) => option.id === val.id}
          renderOption={(props, option) => (
            <Box component="li" {...props} key={option.id}>
              <HomeWorkOutlined fontSize="small" sx={{ mr: 1 }} />
              {option.label}
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
        />
      )}
    />
  );
};

export default TenantFieldController;
