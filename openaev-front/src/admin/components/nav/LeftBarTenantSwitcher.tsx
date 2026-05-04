import { HomeWorkOutlined, UnfoldMoreOutlined } from '@mui/icons-material';
import { ListItemIcon, ListItemText, MenuItem, MenuList, Popover, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useRef, useState } from 'react';

import StyledTooltip from '../../../components/common/menu/leftmenu/StyledTooltip';
import useLeftMenuStyle from '../../../components/common/menu/leftmenu/useLeftMenuStyle';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import type { TenantOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../common/entreprise_edition/EEChip';

interface TenantSwitcherProps { navOpen: boolean }

/**
 * TenantSwitcher component displays a menu-item-style button in the left bar
 * that opens a popover to switch between tenants.
 */
const TenantSwitcher: FunctionComponent<TenantSwitcherProps> = ({ navOpen }) => {
  const { t } = useFormatter();
  const { userTenants, currentUserTenant, switchUserTenant } = useAuth();
  const { isValidated: isValidatedEnterpriseEdition, openDialog } = useEnterpriseEdition();
  const leftMenuStyle = useLeftMenuStyle();

  const [switching, setSwitching] = useState(false);
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const buttonRef = useRef<HTMLLIElement | null>(null);

  const isSelected = useCallback(
    (option: TenantOutput) => option.tenant_id === currentUserTenant?.tenant_id,
    [currentUserTenant],
  );

  const handleOpen = () => {
    if (!isValidatedEnterpriseEdition) {
      openDialog();
      return;
    }
    setAnchorEl(buttonRef.current);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleSwitchTenant = useCallback(async (tenant: TenantOutput) => {
    if (isSelected(tenant)) {
      handleClose();
      return;
    }

    setSwitching(true);
    handleClose();
    try {
      await switchUserTenant(tenant.tenant_id);
    } catch (_error) {
      MESSAGING$.notifyError(t('Error switching tenant'));
    } finally {
      setSwitching(false);
    }
  }, [isSelected, switchUserTenant, t]);

  const displayName = currentUserTenant?.tenant_name ?? t('No tenant');

  return (
    <>
      <StyledTooltip title={displayName} placement="right">
        <MenuItem
          ref={buttonRef}
          onClick={handleOpen}
          disabled={switching}
          dense
          sx={theme => ({
            height: 35,
            paddingRight: theme.spacing(0.25),
          })}
        >
          <ListItemIcon style={{ ...leftMenuStyle.listItemIcon }}>
            {switching ? <Loader variant="inElement" size="xs" /> : <HomeWorkOutlined />}
          </ListItemIcon>
          {navOpen && (
            <>
              <ListItemText
                primary={displayName}
                slotProps={{ primary: { sx: { ...leftMenuStyle.listItemText } } }}
              />
              {!isValidatedEnterpriseEdition && <EEChip clickable featureDetectedInfo="Tenants" />}
              <UnfoldMoreOutlined
                fontSize="small"
                sx={theme => ({
                  color: theme.palette.text.secondary,
                  marginLeft: theme.spacing(0.5),
                })}
              />
            </>
          )}
        </MenuItem>
      </StyledTooltip>
      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'left',
        }}
        slotProps={{
          paper: {
            sx: theme => ({
              backgroundColor: theme.palette.background.nav,
              backgroundImage: 'none',
              minWidth: 180,
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 1,
            }),
          },
        }}
      >
        <MenuList dense>
          {(userTenants ?? []).map((tenant) => {
            const selected = isSelected(tenant);
            return (
              <MenuItem
                key={tenant.tenant_id}
                onClick={() => handleSwitchTenant(tenant)}
                sx={theme => ({
                  'borderLeft': selected ? `3px solid ${theme.palette.primary.main}` : '3px solid transparent',
                  'backgroundColor': selected ? alpha(theme.palette.primary.main, 0.12) : 'transparent',
                  'marginTop': theme.spacing(0.5),
                  'marginBottom': theme.spacing(0.5),
                  '&:hover': { backgroundColor: selected ? alpha(theme.palette.primary.main, 0.18) : theme.palette.action.hover },
                })}
              >
                <ListItemText
                  primary={(
                    <Typography noWrap>
                      {tenant.tenant_name}
                    </Typography>
                  )}
                />
              </MenuItem>
            );
          })}
          {(userTenants ?? []).length === 0 && (
            <MenuItem disabled>
              <ListItemText primary={t('No tenant available')} />
            </MenuItem>
          )}
        </MenuList>
      </Popover>
    </>
  );
};

export default TenantSwitcher;
