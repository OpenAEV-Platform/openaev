import { GroupsOutlined, PermIdentityOutlined, SecurityOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import type { UserHelper } from '../../../../actions/helper';
import {
  fetchPlatformGroupById,
  fetchPlatformGroupRoleIds,
  fetchPlatformGroupUserIds,
} from '../../../../actions/platform/platform-group/platform-group-action';
import { findPlatformRoles } from '../../../../actions/platform/platform-role/platform-role-action';
import { findPlatformUsers } from '../../../../actions/platform/users/platform-user-action';
import { fetchAllRoles, fetchGroupById } from '../../../../actions/security/securityDetail-actions';
import { fetchUsers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, InformationGrid, Section } from '../../../../components/common/detail/EntityDetailCommon';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { GROUP_BASE_URL, ROLE_BASE_URL, USER_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Group, type PlatformGroupOutput, type PlatformRoleOutput, type RoleOutput, type UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import PlatformGroupPopover from '../groups/platform_groups/PlatformGroupPopover';
import GroupPopover from '../groups/tenant_groups/GroupPopover';
import SecurityMenu from '../SecurityMenu';
import useSecurityScope from '../useSecurityScope';
import { SecurityDetailNotFound } from './SecurityDetailNotFound';

interface RelatedItem {
  id: string;
  name: string;
}

const userDisplayName = (user?: UserOutput, fallback = ''): string => {
  if (!user) {
    return fallback;
  }
  return [user.user_firstname, user.user_lastname].filter(Boolean).join(' ').trim() || user.user_email;
};

const GroupDetail = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { groupId } = useParams() as { groupId: string };
  const { scope } = useSecurityScope();
  const isPlatform = scope === 'platform';

  const [group, setGroup] = useState<Group | null>(null);
  const [platformGroup, setPlatformGroup] = useState<PlatformGroupOutput | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [roles, setRoles] = useState<RoleOutput[]>([]);
  const [platformMembers, setPlatformMembers] = useState<UserOutput[]>([]);
  const [platformRoles, setPlatformRoles] = useState<PlatformRoleOutput[]>([]);

  const { usersMap } = useHelper((helper: UserHelper) => ({ usersMap: helper.getUsersMap() }));
  useDataLoader(() => {
    if (!isPlatform) {
      dispatch(fetchUsers());
    }
  });

  useEffect(() => {
    setGroup(null);
    setPlatformGroup(null);
    setNotFound(false);
    if (isPlatform) {
      fetchPlatformGroupById(groupId)
        .then(response => setPlatformGroup(response.data as PlatformGroupOutput))
        .catch(() => setNotFound(true));
      fetchPlatformGroupUserIds(groupId)
        .then((response) => {
          const ids = (response.data ?? []) as string[];
          if (ids.length === 0) {
            setPlatformMembers([]);
            return;
          }
          findPlatformUsers(ids).then(usersResponse => setPlatformMembers((usersResponse.data ?? []) as UserOutput[]));
        });
      fetchPlatformGroupRoleIds(groupId)
        .then((response) => {
          const ids = (response.data ?? []) as string[];
          if (ids.length === 0) {
            setPlatformRoles([]);
            return;
          }
          findPlatformRoles(ids).then(rolesResponse => setPlatformRoles((rolesResponse.data ?? []) as PlatformRoleOutput[]));
        });
    } else {
      fetchGroupById(groupId)
        .then(response => setGroup(response.data as Group))
        .catch(() => setNotFound(true));
      fetchAllRoles().then(response => setRoles(response.data as RoleOutput[]));
    }
  }, [groupId, isPlatform]);

  const rolesMap = useMemo(
    () => Object.fromEntries(roles.map(role => [role.role_id, role.role_name])),
    [roles],
  );

  const groupsLink = isPlatform ? `${GROUP_BASE_URL}?scope=platform` : GROUP_BASE_URL;
  const scopeSuffix = isPlatform ? '?scope=platform' : '';

  if (notFound) {
    return <SecurityDetailNotFound>{t('This group could not be found.')}</SecurityDetailNotFound>;
  }

  if (isPlatform ? !platformGroup : !group) {
    return <Loader />;
  }

  const title = isPlatform ? platformGroup!.platform_group_name : group!.group_name;
  const description = isPlatform ? platformGroup!.platform_group_description : group!.group_description;
  const defaultUserAssign = isPlatform ? platformGroup!.group_default_user_assign : group!.group_default_user_assign;

  const roleItems: RelatedItem[] = isPlatform
    ? platformRoles.map(role => ({
        id: role.platform_role_id,
        name: role.platform_role_name,
      }))
    : (group!.group_roles ?? []).map(roleId => ({
        id: roleId,
        name: rolesMap[roleId] ?? roleId,
      }));

  const memberItems: RelatedItem[] = isPlatform
    ? platformMembers.map(member => ({
        id: member.user_id,
        name: userDisplayName(member, member.user_id),
      }))
    : (group!.group_users ?? []).map(memberId => ({
        id: memberId,
        name: userDisplayName(usersMap[memberId], memberId),
      }));

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="object"
          elements={[
            { label: t(SETTINGS_LABEL) },
            { label: t('Security') },
            {
              label: t('Groups'),
              link: groupsLink,
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
            icon={GroupsOutlined}
            title={title}
            chips={(
              <>
                <Chip size="small" variant="outlined" label={t('{count} members', { count: memberItems.length })} sx={{ borderRadius: 1 }} />
                <Chip size="small" variant="outlined" label={t('{count} roles', { count: roleItems.length })} sx={{ borderRadius: 1 }} />
              </>
            )}
            action={isPlatform
              ? (
                  <PlatformGroupPopover
                    platformGroup={platformGroup!}
                    actions={['Update', 'Manage users', 'Manage roles', 'Delete']}
                    onUpdate={(updated: PlatformGroupOutput) => setPlatformGroup(updated)}
                    onDelete={() => navigate(groupsLink)}
                  />
                )
              : (
                  <GroupPopover
                    group={group!}
                    groupUsersIds={group!.group_users ?? []}
                    groupRolesIds={group!.group_roles ?? []}
                    onUpdate={(updated: Group) => setGroup(updated)}
                    onDelete={() => navigate(groupsLink)}
                  />
                )}
          />

          <InformationGrid title={t('Information')}>
            <Field label={t('Description')}>{description || '-'}</Field>
            <Field label={t('Default user assignment')}>{defaultUserAssign ? t('Yes') : t('No')}</Field>
          </InformationGrid>

          <DetailSections>
            <Section title={t('Roles')}>
              {roleItems.length === 0
                ? <Empty message={t('No role assigned to this group.')} />
                : (
                    <List disablePadding>
                      {roleItems.map(role => (
                        <ListItem key={role.id} divider disablePadding>
                          <ListItemButton component={Link} to={`${ROLE_BASE_URL}/${role.id}${scopeSuffix}`}>
                            <ListItemIcon sx={{ minWidth: 36 }}><SecurityOutlined color="primary" /></ListItemIcon>
                            <ListItemText primary={role.name} />
                          </ListItemButton>
                        </ListItem>
                      ))}
                    </List>
                  )}
            </Section>

            <Section title={t('Members')}>
              {memberItems.length === 0
                ? <Empty message={t('No member in this group.')} />
                : (
                    <List disablePadding>
                      {memberItems.map(member => (
                        <ListItem key={member.id} divider disablePadding>
                          <ListItemButton component={Link} to={`${USER_BASE_URL}/${member.id}${scopeSuffix}`}>
                            <ListItemIcon sx={{ minWidth: 36 }}><PermIdentityOutlined color="primary" /></ListItemIcon>
                            <ListItemText primary={member.name} />
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
  );
};

export default GroupDetail;
