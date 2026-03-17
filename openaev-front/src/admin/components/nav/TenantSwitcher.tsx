import { Business as BusinessIcon, Check as CheckIcon, ExpandMore as ExpandMoreIcon } from '@mui/icons-material';
import { Avatar, Button, CircularProgress, ListItemIcon, ListItemText, Menu, MenuItem, Tooltip } from '@mui/material';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useCallback, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import { MESSAGING$ } from "../../../utils/Environment";

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
      await switchUserTenant(tenantId)
      setSwitching(false);
      handleClose();
    } catch (error) {
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
          startIcon={
            <Avatar
              sx={{ width: 24, height: 24, bgcolor: 'primary.main' }}
            >
              <BusinessIcon fontSize="small" />
            </Avatar>
          }
          endIcon={<ExpandMoreIcon />}
          disabled={switching}
          sx={{
            textTransform: 'none',
            color: 'text.primary',
            width: 220,
            '&:hover': {
              backgroundColor: 'action.hover',
            },
          }}
          aria-controls={menuOpen ? 'tenant-switcher-menu' : undefined}
          aria-haspopup="true"
          aria-expanded={menuOpen ? 'true' : undefined}
        >
          {switching ? (
            <CircularProgress size={16} sx={{ ml: 1 }} />
          ) : (
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {currentUserTenant?.tenant_name || t('Select Tenant')}
            </span>
          )}
        </Button>
      </Tooltip>

      <Menu
        id="tenant-switcher-menu"
        anchorEl={anchorEl}
        open={menuOpen}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        slotProps={{
          paper: {
            elevation: 3,
            sx: {
              width: 280,
              mt: 1,
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
                  width: 32,
                  height: 32,
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
                  fontWeight: tenant.tenant_id === currentUserTenant?.tenant_id ? 600 : 400,
                  noWrap: true,
                },
                secondary: {
                  noWrap: true,
                },
              }}
              sx={{ overflow: 'hidden' }}
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
