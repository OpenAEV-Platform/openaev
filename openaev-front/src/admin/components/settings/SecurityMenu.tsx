import {
  DomainOutlined,
  GroupsOutlined,
  HomeWorkOutlined,
  KeyOutlined,
  LocalPoliceOutlined,
  PermIdentityOutlined,
  PublicOutlined,
  SecurityOutlined,
} from '@mui/icons-material';
import { Box, ToggleButton, ToggleButtonGroup } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useContext } from 'react';
import { useLocation, useNavigate } from 'react-router';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';
import { useFormatter } from '../../../components/i18n';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import EEChip from '../common/entreprise_edition/EEChip';
import useSecurityScope, { type SecurityScope } from './useSecurityScope';

const SECURITY_BASE = '/admin/settings/security';

// Entities that exist in both scopes, so switching scope keeps the entity.
const SCOPED_ENTITIES = ['users', 'groups', 'roles', 'sessions'];

const SecurityMenuComponent: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const { isValidated: isEnterpriseEdition, openDialog } = useEnterpriseEdition();
  const { scope, canAccessTenant, canAccessPlatform } = useSecurityScope();
  const ability = useContext(AbilityContext);
  const canAccessTenants = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS);

  const showScopeSwitch = canAccessTenant && canAccessPlatform;
  const isPlatform = scope === 'platform';

  // Carry the current scope on the shared entity links so navigating within
  // the section preserves the chosen context.
  const scopedPath = (entity: string) => `${SECURITY_BASE}/${entity}${isPlatform ? '?scope=platform' : ''}`;

  const changeScope = (next: SecurityScope) => {
    if (next === 'platform' && !isEnterpriseEdition) {
      openDialog();
      return;
    }
    // Keep the current entity when it exists in the target scope, else land on Users.
    const currentEntity = location.pathname.replace(`${SECURITY_BASE}/`, '').split('/')[0];
    const entity = SCOPED_ENTITIES.includes(currentEntity) ? currentEntity : 'users';
    navigate(`${SECURITY_BASE}/${entity}${next === 'platform' ? '?scope=platform' : ''}`);
  };

  // The menu reshapes with the selected scope (single context selector on top,
  // no per-entity repetition):
  // - This tenant: Users / Groups / Organizations / Roles / Sessions / Policies.
  // - Platform: platform-wide Users / Groups / Roles plus the Tenants registry.
  const scopedEntries: RightMenuEntry[] = (canAccessTenant || canAccessPlatform)
    ? [
        {
          path: scopedPath('users'),
          activePath: `${SECURITY_BASE}/users`,
          icon: () => (<PermIdentityOutlined />),
          label: 'Users',
        },
        {
          path: scopedPath('groups'),
          activePath: `${SECURITY_BASE}/groups`,
          icon: () => (<GroupsOutlined />),
          label: 'Groups',
        },
        {
          path: scopedPath('roles'),
          activePath: `${SECURITY_BASE}/roles`,
          icon: () => (<SecurityOutlined />),
          label: 'Roles',
        },
      ]
    : [];

  const entries: RightMenuEntry[] = isPlatform
    ? [
        ...scopedEntries,
        ...(canAccessPlatform
          ? [{
              path: scopedPath('sessions'),
              activePath: `${SECURITY_BASE}/sessions`,
              icon: () => (<KeyOutlined />),
              label: 'Sessions',
            }]
          : []),
        ...(canAccessTenants
          ? [{
              path: `${SECURITY_BASE}/tenants`,
              icon: () => (<HomeWorkOutlined />),
              label: 'Tenants',
              chip: !isEnterpriseEdition ? (<EEChip clickable />) : undefined,
              onClick: !isEnterpriseEdition ? () => openDialog() : undefined,
            }]
          : []),
      ]
    : [
        ...scopedEntries,
        ...(canAccessTenant
          ? [
              {
                path: `${SECURITY_BASE}/organizations`,
                icon: () => (<DomainOutlined />),
                label: 'Organizations',
              },
              {
                path: scopedPath('sessions'),
                activePath: `${SECURITY_BASE}/sessions`,
                icon: () => (<KeyOutlined />),
                label: 'Sessions',
              },
              {
                path: `${SECURITY_BASE}/policies`,
                icon: () => (<LocalPoliceOutlined />),
                label: 'Policies',
              },
            ]
          : []),
      ];

  // Single context selector at the top of the section (industry pattern: scope
  // is a primary navigation constraint expressed once, not a per-resource
  // filter repeated under every entry).
  const scopeSwitcher = showScopeSwitch
    ? (
        <Box sx={{ padding: theme.spacing(1, 1.5, 1, 1.5) }}>
          <ToggleButtonGroup
            exclusive
            size="small"
            fullWidth
            value={scope}
            onChange={(_event, value: SecurityScope | null) => {
              if (value && value !== scope) changeScope(value);
            }}
            sx={{
              'gap': 1,
              // The global MuiToggleButtonGroup override pins the group to 36px
              // (fine for single-line toggles). This switcher stacks an icon over
              // a label, so let its height grow with the content + padding.
              'height': 'auto',
              '& .MuiToggleButton-root': {
                'flexDirection': 'column',
                'gap': 0.75,
                'paddingBlock': 1.75,
                'paddingInline': 1.5,
                'borderRadius': 1,
                'border': `1px solid ${theme.palette.divider}`,
                'textTransform': 'none',
                'fontSize': 11,
                'fontWeight': 600,
                'lineHeight': 1.2,
                'color': theme.palette.text.secondary,
                '& .MuiSvgIcon-root': { fontSize: 18 },
                '&.Mui-selected': {
                  'backgroundColor': alpha(theme.palette.primary.main, 0.16),
                  'color': theme.palette.primary.main,
                  '&:hover': { backgroundColor: alpha(theme.palette.primary.main, 0.22) },
                },
              },
            }}
          >
            <ToggleButton value="tenant">
              <DomainOutlined />
              {t('This tenant')}
            </ToggleButton>
            <ToggleButton value="platform">
              <PublicOutlined />
              {t('Platform')}
              {!isEnterpriseEdition && <EEChip />}
            </ToggleButton>
          </ToggleButtonGroup>
        </Box>
      )
    : undefined;

  return (
    <RightMenu entries={entries} header={scopeSwitcher} />
  );
};

const SecurityMenu = memo(SecurityMenuComponent);

export default SecurityMenu;
