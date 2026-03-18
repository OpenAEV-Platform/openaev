import { Business as BusinessIcon, Check as CheckIcon, ExpandMore as ExpandMoreIcon } from '@mui/icons-material';
import { Avatar, Button, CircularProgress, ListItemIcon, ListItemText, Menu, MenuItem, Tooltip } from '@mui/material';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useCallback, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { MESSAGING$ } from '../../../utils/Environment';
import useAuth from '../../../utils/hooks/useAuth';

/**
 * TenantSwitcher component displays a dropdown allowing users to switch
 * between tenants they have access to.
 */
const TenantSwitcher: FunctionComponent = () => {
  const { t } = useFormatter();
  const { userTenants, currentUserTenant, switchUserTenant } = useAuth();

  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [switching, setSwitching] = useState(false);

  const handleOpen = useCallback((event: ReactMouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  }, []);

  const handleClose = useCallback(() => {
    setAnchorEl(null);
  }, []);

  const handleSwitchTenant = useCallback(async (tenantId: string) => {
    // If already on this tenant, just close
    if (tenantId === currentUserTenant?.tenant_id) {
      handleClose();
      return;
    }

    setSwitching(true);
    try {
      await switchUserTenant(tenantId);
      setSwitching(false);
      handleClose();
    } catch (_error) {
      setSwitching(false);
      MESSAGING$.notifyError(t('Error switching tenant'));
    }
  }, [currentUserTenant, handleClose]);

  const menuOpen = Boolean(anchorEl);

  return (
    <>
      <Tooltip title={t('Switch tenant')}>
        <Button
          onClick={handleOpen}
          startIcon={(
            <Avatar
              sx={{
                width: (theme) => theme.spacing(3),
                height: (theme) => theme.spacing(3),
                bgcolor: 'primary.main',
              }}
            >
              <BusinessIcon fontSize="small" />
            </Avatar>
          )}
          endIcon={<ExpandMoreIcon />}
          disabled={switching}
        sx={{
          textTransform: 'none',
          color: 'text.primary',
          maxWidth: 220,
        }}
          aria-controls={menuOpen ? 'tenant-switcher-menu' : undefined}
          aria-haspopup="true"
          aria-expanded={menuOpen ? 'true' : undefined}
        >
          {switching ? (
            <CircularProgress size={16} sx={{ ml: 1 }} />
          ) : (
            <span style={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            >
              {currentUserTenant?.tenant_name || t('Select tenant')}
            </span>
          )}
        </Button>
      </Tooltip>

      <Menu
        id="tenant-switcher-menu"
        anchorEl={anchorEl}
        open={menuOpen}
        onClose={handleClose}
        slotProps={{
          paper: {
            sx: {
              width: 280,
            },
          },
        }}
      >
        {userTenants.map(tenant => (
          <MenuItem
            key={tenant.tenant_id}
            onClick={() => handleSwitchTenant(tenant.tenant_id)}
            selected={tenant.tenant_id === currentUserTenant?.tenant_id}
            disabled={switching}
          >
            <ListItemIcon>
              <Avatar
                sx={{
                  bgcolor: 'primary.main',
                }}
              >
                {tenant.tenant_name.charAt(0).toUpperCase()}
              </Avatar>
            </ListItemIcon>
            <ListItemText
              primary={tenant.tenant_name}
              secondary={tenant.tenant_description}
              slotProps={{
                primary: {
                  fontWeight: (theme) => (tenant.tenant_id === currentUserTenant?.tenant_id ? theme.typography.fontWeightBold : theme.typography.fontWeightRegular),
                  noWrap: true,
                },
                secondary: { noWrap: true },
              }}
              sx={{ overflow: 'hidden', ml: 1 }}
            />
            {tenant.tenant_id === currentUserTenant?.tenant_id && (
              <CheckIcon fontSize="small" color="primary" sx={{ ml: 1 }} />
            )}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
};

export default TenantSwitcher;
