import {
  Combobox,
  ComboboxChips,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { HomeWorkOutlined } from '@mui/icons-material';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { searchTenants } from '../../actions/platform/tenants/tenant-action';
import type { TenantOutput } from '../../utils/api-types';
import type { Option } from '../../utils/Option';

const useStyles = makeStyles()(theme => ({ icon: { marginRight: theme.spacing(1) } }));

interface Props {
  name: string;
  label: string;
  disabled?: boolean;
}

const TenantFieldController: FunctionComponent<Props> = ({ name, label, disabled = false }) => {
  const { classes } = useStyles();
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
        <Combobox<Option>
          multiple
          disabled={disabled}
          options={options}
          value={options.filter(o => ((value as string[]) ?? []).includes(o.id))}
          onValueChange={next => onChange((next as Option[]).map(v => v.id))}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(option, val) => option.id === val.id}
          error={!!error}
          renderOption={option => (
            <>
              <HomeWorkOutlined fontSize="small" className={classes.icon} />
              {option.label}
            </>
          )}
        >
          <ComboboxLabel>{label}</ComboboxLabel>
          <ComboboxField>
            <ComboboxChips />
            <ComboboxInput />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
          {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
        </Combobox>
      )}
    />
  );
};

export default TenantFieldController;
