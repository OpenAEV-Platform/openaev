import { ArrowDropDown, OpenInNew } from '@mui/icons-material';
import { Box, Divider, IconButton, List, ListItemButton, ListItemIcon, Popover, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { type LoggedHelper } from '../../../actions/helper';
import { fetchXtmHubRegistration } from '../../../actions/xtmhub/xtmhub-actions';
import { useFormatter } from '../../../components/i18n';
import { REDIRECT_CONNECT_XTM_HUB_URL } from '../../../constants/BaseUrls';
import logoOpenCtiDark from '../../../static/images/logo_open_cti_dark.svg';
import logoOpenCtiLight from '../../../static/images/logo_open_cti_light.svg';
import logoXtmHubDark from '../../../static/images/logo_xtm_hub_dark.svg';
import logoXtmHubLight from '../../../static/images/logo_xtm_hub_light.svg';
import { useHelper } from '../../../store';
import { fileUri, XTM_HUB_DEFAULT_URL } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

interface PopoverItemProps {
  logo: string;
  alt: string;
  tooltip: string;
  href?: string;
  to?: string;
  onClick: () => void;
}

// One product row of the switcher popover (OpenCTI LeftBarHeader pattern):
// product logo on the left, external-link icon on the right.
const PopoverListItem: FunctionComponent<PopoverItemProps> = ({ logo, alt, tooltip, href, to, onClick }) => {
  const theme = useTheme();
  const linkProps = href
    ? {
        component: 'a' as const,
        href,
        target: '_blank',
        rel: 'noreferrer',
      }
    : {
        component: Link,
        to: to ?? '',
      };
  return (
    <Tooltip title={tooltip} placement="right">
      <ListItemButton
        {...linkProps}
        onClick={onClick}
        sx={{
          borderRadius: 1,
          px: 1,
          py: 1.5,
          justifyContent: 'space-between',
          backgroundColor: theme.palette.leftBar?.header?.itemBackground,
        }}
      >
        <ListItemIcon sx={{
          width: 132,
          p: 1,
        }}
        >
          <Box sx={{
            width: '100%',
            height: '20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          >
            <img
              src={fileUri(logo)}
              alt={alt}
              style={{
                width: '100%',
                height: 'auto',
                objectFit: 'contain',
              }}
            />
          </Box>
        </ListItemIcon>
        {href && <OpenInNew sx={{ fontSize: 16 }} />}
      </ListItemButton>
    </Tooltip>
  );
};

interface Props { navOpen: boolean }

/**
 * Header of the left navigation drawer, aligned with OpenCTI's LeftBarHeader:
 * the platform logo (link to home) plus an arrow trigger opening the Filigran
 * product-switcher popover (OpenCTI / XTM Hub).
 */
const LeftBarHeader: FunctionComponent<Props> = ({ navOpen }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useAbility();
  const { settings, isXTMHubAccessible } = useAuth();

  useEffect(() => {
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS)) {
      dispatch(fetchXtmHubRegistration());
    }
  }, []);

  const tenantSettings = useHelper((helper: LoggedHelper) => helper.getTenantSettings());
  const registration = useHelper((helper: LoggedHelper) => helper.getXtmHubRegistration());
  const isRegistered = registration?.tenant_xtmhub_registration_status === 'REGISTERED';
  const shouldXtmHubRedirectToSite = isRegistered || !isXTMHubAccessible || !ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);

  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);
  const handleOpen = (event: ReactMouseEvent<HTMLButtonElement, MouseEvent>) => {
    event.preventDefault();
    setAnchorEl(event.currentTarget);
  };
  const handleClose = () => setAnchorEl(null);
  const open = Boolean(anchorEl);

  const isDark = theme.palette.mode === 'dark';
  const isOpenCtiConnected = !!(tenantSettings?.xtm_opencti_enable && tenantSettings?.xtm_opencti_url);
  const openCtiUrl = isOpenCtiConnected
    ? tenantSettings.xtm_opencti_url
    : 'https://filigran.io/platform/opencti/';
  const xtmHubUrl = settings.xtm_hub_enable && settings.xtm_hub_url
    ? settings.xtm_hub_url
    : XTM_HUB_DEFAULT_URL;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: navOpen ? 'flex-start' : 'center',
        gap: theme.spacing(0.5),
        padding: theme.spacing(1.5, 1),
        minHeight: 56,
      }}
    >
      {/* Expanded: the logo fills the remaining header width (flex: 1) so wide
          wordmarks like OpenAEV's render at a comfortable height instead of being
          capped short by a fixed maxWidth. Collapsed: a fixed square emblem. */}
      <Link
        to="/admin"
        style={{
          display: 'flex',
          alignItems: 'center',
          minWidth: 0,
          ...(navOpen ? { flex: 1 } : {}),
        }}
      >
        <img
          src={navOpen ? theme.logo : theme.logo_collapsed}
          alt="logo"
          style={navOpen
            ? {
                cursor: 'pointer',
                width: '100%',
                height: 'auto',
                maxHeight: 40,
                objectFit: 'contain',
                objectPosition: 'left center',
              }
            : {
                cursor: 'pointer',
                height: 35,
                maxWidth: 23,
                objectFit: 'contain',
              }}
        />
      </Link>
      {navOpen && (
        <>
          <IconButton
            size="small"
            color="primary"
            aria-label={t('Filigran products')}
            onClick={handleOpen}
            sx={{
              borderRadius: 1,
              flexShrink: 0,
              transition: 'transform 0.2s',
              transform: open ? 'rotate(180deg)' : 'none',
            }}
          >
            <ArrowDropDown />
          </IconButton>
          <Popover
            anchorEl={anchorEl}
            open={open}
            onClose={handleClose}
            anchorOrigin={{
              vertical: 'bottom',
              horizontal: 'right',
            }}
            transformOrigin={{
              vertical: 'top',
              horizontal: 'left',
            }}
            slotProps={{ paper: { sx: { transform: 'translateX(-40px)' } } }}
          >
            <List dense sx={{ minWidth: 228 }}>
              <PopoverListItem
                logo={isDark ? logoOpenCtiDark : logoOpenCtiLight}
                alt="OpenCTI"
                tooltip={isOpenCtiConnected ? t('Platform connected') : t('Get OpenCTI now')}
                href={openCtiUrl}
                onClick={handleClose}
              />
              <Divider />
              {shouldXtmHubRedirectToSite
                ? (
                    <PopoverListItem
                      logo={isDark ? logoXtmHubDark : logoXtmHubLight}
                      alt="XTM Hub"
                      tooltip={isRegistered ? t('Platform connected') : t('Get XTM Hub now')}
                      href={xtmHubUrl}
                      onClick={handleClose}
                    />
                  )
                : (
                    <PopoverListItem
                      logo={isDark ? logoXtmHubDark : logoXtmHubLight}
                      alt="XTM Hub"
                      tooltip={t('Connect your product')}
                      to={REDIRECT_CONNECT_XTM_HUB_URL}
                      onClick={handleClose}
                    />
                  )}
            </List>
          </Popover>
        </>
      )}
    </div>
  );
};

export default LeftBarHeader;
