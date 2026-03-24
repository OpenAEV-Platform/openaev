import { CheckOutlined } from '@mui/icons-material';
import { Autocomplete, Box, TextField, Typography } from '@mui/material';
import { type FunctionComponent, useCallback, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import type { TenantOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import useAuth from '../../../utils/hooks/useAuth';

/**
 * TenantSwitcher component displays an autocomplete dropdown allowing users
 * to switch between tenants they have access to.
 */
const TenantSwitcher: FunctionComponent = () => {
  const { t } = useFormatter();
  const { userTenants, currentUserTenant, switchUserTenant } = useAuth();

  const [switching, setSwitching] = useState(false);

  const isSelected = useCallback(
    (option: TenantOutput) => option.tenant_id === currentUserTenant?.tenant_id,
    [currentUserTenant],
  );

  const handleSwitchTenant = useCallback(async (_event: unknown, tenant: TenantOutput | null) => {
    if (!tenant || isSelected(tenant)) {
      return;
    }

    setSwitching(true);
    try {
      await switchUserTenant(tenant.tenant_id);
    } catch (_error) {
      MESSAGING$.notifyError(t('Error switching tenant'));
    } finally {
      setSwitching(false);
    }
  }, [isSelected, switchUserTenant, t]);

  return (
    <Autocomplete
      sx={theme => ({
        display: 'inline-flex',
        verticalAlign: 'middle',
        width: theme.spacing(28),
        mr: theme.spacing(1),
      })}
      value={currentUserTenant ?? undefined}
      options={userTenants}
      onChange={handleSwitchTenant}
      getOptionLabel={option => option.tenant_name}
      isOptionEqualToValue={(option, value) => option.tenant_id === value.tenant_id}
      disabled={switching}
      disableClearable
      openOnFocus
      autoHighlight
      noOptionsText={t('No available options')}
      renderInput={params => (
        <TextField
          {...params}
          variant="outlined"
          size="small"
          slotProps={{
            input: {
              ...params.InputProps,
              sx: theme => ({ backgroundColor: theme.palette.background.paper }),
              endAdornment: (
                <>
                  {switching ? <Loader variant="inElement" size="xs" /> : null}
                  {params.InputProps.endAdornment}
                </>
              ),
            },
          }}
        />
      )}
      renderOption={(props, option) => {
        const selected = isSelected(option);
        return (
          <li {...props} key={option.tenant_id}>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              width: '100%',
              overflow: 'hidden',
            }}
            >
              <Box sx={{
                overflow: 'hidden',
                flex: 1,
              }}
              >
                <Typography noWrap sx={{ textOverflow: 'ellipsis' }}>{option.tenant_name}</Typography>
                {option.tenant_description && (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    noWrap
                    sx={{
                      display: 'block',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {option.tenant_description}
                  </Typography>
                )}
              </Box>
              {selected && (
                <CheckOutlined
                  color="primary"
                  fontSize="small"
                  sx={theme => ({
                    ml: theme.spacing(1),
                    flexShrink: 0,
                  })}
                />
              )}
            </Box>
          </li>
        );
      }}
    />
  );
};

export default TenantSwitcher;
