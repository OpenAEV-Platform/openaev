import { GroupsOutlined, LocalPoliceOutlined, SecurityOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { type ReactElement, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import {
  fetchPlatformGroupRoleIds,
  searchPlatformGroups,
} from '../../../../actions/platform/platform-group/platform-group-action';
import { fetchPlatformRoleById } from '../../../../actions/platform/platform-role/platform-role-action';
import { fetchAllGroups, fetchRoleById } from '../../../../actions/security/securityDetail-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, InformationGrid, Section } from '../../../../components/common/detail/EntityDetailCommon';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { GROUP_BASE_URL, ROLE_BASE_URL } from '../../../../constants/BaseUrls';
import { type CapabilityOutput, type Group, type PlatformGroupOutput, type RoleOutput } from '../../../../utils/api-types';
import useCapabilities from '../../../../utils/hooks/useCapabilities';
import { CAPABILITY_SCOPES } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import RolePopover from '../roles/RolePopover';
import RoleScopeProvider from '../roles/RoleScopeProvider';
import SecurityMenu from '../SecurityMenu';
import useSecurityScope from '../useSecurityScope';
import { SecurityDetailNotFound } from './SecurityDetailNotFound';

// Turns MANAGE_ASSESSMENT into "Manage assessment" for display.
const humanizeCapability = (capability: string): string => {
  const lowered = capability.replace(/_/g, ' ').toLowerCase();
  return lowered.charAt(0).toUpperCase() + lowered.slice(1);
};

// Renders the capability tree top-down, indenting each level, and keeping only
// branches that lead to a granted capability (same read-only philosophy as
// OpenCTI: show what the role actually grants, in its hierarchy). A node that
// is only an ancestor of a granted capability is dimmed.
const renderCapabilityNodes = (
  nodes: CapabilityOutput[],
  granted: Set<string>,
  depth: number,
): ReactElement[] => nodes.flatMap((node) => {
  const isGranted = granted.has(node.capability_value);
  const children = renderCapabilityNodes(node.capability_children ?? [], granted, depth + 1);
  if (!isGranted && children.length === 0) {
    return [];
  }
  return [
    <Box
      key={node.capability_value}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        paddingLeft: `${depth * 20}px`,
        paddingBlock: 0.5,
        opacity: isGranted ? 1 : 0.45,
      }}
    >
      <LocalPoliceOutlined sx={{
        fontSize: 16,
        color: isGranted ? 'primary.main' : 'text.secondary',
      }}
      />
      <Typography variant="body2">{humanizeCapability(node.capability_value)}</Typography>
    </Box>,
    ...children,
  ];
});

const RoleDetail = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const { roleId } = useParams() as { roleId: string };
  const { scope } = useSecurityScope();
  const isPlatform = scope === CAPABILITY_SCOPES.PLATFORM;
  const { capabilities: capabilityTree } = useCapabilities(scope);

  const [role, setRole] = useState<RoleOutput | null>(null);
  const [platformRole, setPlatformRole] = useState<RoleOutput | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [groups, setGroups] = useState<Group[]>([]);
  const [platformGroupsUsingRole, setPlatformGroupsUsingRole] = useState<PlatformGroupOutput[]>([]);

  useEffect(() => {
    setRole(null);
    setPlatformRole(null);
    setNotFound(false);
    setPlatformGroupsUsingRole([]);
    if (isPlatform) {
      fetchPlatformRoleById(roleId)
        .then(response => setPlatformRole(response.data as RoleOutput))
        .catch(() => setNotFound(true));
      // Platform groups don't carry their role ids in the list output, so resolve
      // "groups using this role" by intersecting each group's roles with this role
      // (kept parallel to the tenant view for a consistent overview).
      searchPlatformGroups(buildSearchPagination({ size: 1000 })).then((response) => {
        const platformGroups = (response.data?.content ?? []) as PlatformGroupOutput[];
        Promise.all(
          platformGroups.map(group =>
            fetchPlatformGroupRoleIds(group.platform_group_id)
              .then(r => (((r.data ?? []) as string[]).includes(roleId) ? group : null))
              .catch(() => null)),
        ).then(resolved =>
          setPlatformGroupsUsingRole(
            resolved.filter((g): g is PlatformGroupOutput => g !== null)));
      });
    } else {
      fetchRoleById(roleId)
        .then(response => setRole(response.data as RoleOutput))
        .catch(() => setNotFound(true));
      fetchAllGroups().then(response => setGroups((response.data?.content ?? []) as Group[]));
    }
  }, [roleId, isPlatform]);

  const capabilities = (isPlatform ? platformRole : role)?.role_capabilities ?? [];
  const grantedSet = useMemo(() => new Set(capabilities), [capabilities]);
  const groupsUsingRole = useMemo(
    () => (isPlatform
      ? platformGroupsUsingRole.map(group => ({
          id: group.platform_group_id,
          name: group.platform_group_name,
        }))
      : groups
          .filter(group => (group.group_roles ?? []).includes(roleId))
          .map(group => ({
            id: group.group_id,
            name: group.group_name,
          }))),
    [isPlatform, platformGroupsUsingRole, groups, roleId],
  );

  const rolesLink = isPlatform ? `${ROLE_BASE_URL}?scope=platform` : ROLE_BASE_URL;
  const scopeSuffix = isPlatform ? '?scope=platform' : '';

  if (notFound) {
    return <SecurityDetailNotFound>{t('This role could not be found.')}</SecurityDetailNotFound>;
  }

  if (isPlatform ? !platformRole : !role) {
    return <Loader />;
  }

  const title = isPlatform ? platformRole!.role_name : role!.role_name;
  const description = isPlatform ? platformRole!.role_description : role!.role_description;

  return (
    <RoleScopeProvider scope={scope}>
      <div style={{ display: 'flex' }}>
        <div style={{ flexGrow: 1 }}>
          <Breadcrumbs
            variant="object"
            elements={[
              { label: t(SETTINGS_LABEL) },
              { label: t('Security') },
              {
                label: t('Roles'),
                link: rolesLink,
              },
              {
                label: title,
                current: true,
              },
            ]}
          />
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
          >
            <DetailHero
              icon={SecurityOutlined}
              title={title}
              chips={(
                <Chip
                  size="small"
                  variant="outlined"
                  label={t('{count} capabilities', { count: capabilities.length })}
                  sx={{ borderRadius: 1 }}
                />
              )}
              action={(
                <RolePopover
                  role={(isPlatform ? platformRole : role)!}
                  onUpdate={updated => (isPlatform ? setPlatformRole(updated) : setRole(updated))}
                  onDelete={() => navigate(rolesLink)}
                />
              )}
            />

            {/* All short sections share one adaptive grid so the overview stays
                compact (they stack automatically on narrow viewports). */}
            <DetailSections>
              <InformationGrid title={t('Information')}>
                <Field label={t('Description')}>{description || '-'}</Field>
              </InformationGrid>
              <Section title={t('Capabilities')}>
                {capabilities.length === 0
                  ? <Empty message={t('No capability granted by this role.')} />
                  : <div>{renderCapabilityNodes(capabilityTree, grantedSet, 0)}</div>}
              </Section>

              <Section title={t('Groups using this role')}>
                {groupsUsingRole.length === 0
                  ? <Empty message={t('No group uses this role.')} />
                  : (
                      <List disablePadding>
                        {groupsUsingRole.map(group => (
                          <ListItem key={group.id} divider disablePadding>
                            <ListItemButton component={Link} to={`${GROUP_BASE_URL}/${group.id}${scopeSuffix}`}>
                              <ListItemIcon sx={{ minWidth: 36 }}><GroupsOutlined color="primary" /></ListItemIcon>
                              <ListItemText primary={group.name} />
                            </ListItemButton>
                          </ListItem>
                        ))}
                      </List>
                    )}
              </Section>
            </DetailSections>
          </Box>
        </div>
        <SecurityMenu />
      </div>
    </RoleScopeProvider>
  );
};

export default RoleDetail;
