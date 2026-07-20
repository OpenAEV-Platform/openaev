import { GroupsOutlined, PermIdentityOutlined, VerifiedUserOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { fetchPlatformUserById } from '../../../../actions/platform/users/platform-user-action';
import { fetchAllGroups, fetchUserById } from '../../../../actions/security/securityDetail-actions';
import { fetchUserSessions, killSession } from '../../../../actions/sessions/session-actions';
import { deleteUser, updateUser, updateUserPassword } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, InformationGrid, Section } from '../../../../components/common/detail/EntityDetailCommon';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import { GROUP_BASE_URL, USER_BASE_URL } from '../../../../constants/BaseUrls';
import { type ChangePasswordInput, type Group, type SessionOutput, type UserInput, type UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import SessionsTable from '../sessions/SessionsTable';
import PlatformUserPopover from '../users/platform_users/PlatformUserPopover';
import UserPopover from '../users/tenant_users/UserPopover';
import useSecurityScope from '../useSecurityScope';
import { SecurityDetailNotFound } from './SecurityDetailNotFound';

const UserDetail = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { userId } = useParams() as { userId: string };
  const { scope } = useSecurityScope();
  const isPlatform = scope === 'platform';

  const [user, setUser] = useState<UserOutput | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [sessions, setSessions] = useState<SessionOutput[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);

  const loadSessions = useCallback(() => {
    fetchUserSessions(userId).then(response => setSessions(response.data as SessionOutput[]));
  }, [userId]);
  const loadUser = useCallback(() => {
    // Platform users are global and are absent from the tenant-scoped
    // /api/users endpoint, so we must hit the dedicated platform endpoint;
    // otherwise the request 404s and the page spins forever.
    const request = isPlatform ? fetchPlatformUserById(userId) : fetchUserById(userId);
    request
      .then(response => setUser(response.data as UserOutput))
      .catch(() => setNotFound(true));
  }, [userId, isPlatform]);
  useEffect(() => {
    setUser(null);
    setNotFound(false);
    loadUser();
    // Sessions and the tenant group membership list are tenant-scoped concepts;
    // for platform users we surface their tenant memberships instead (below).
    if (!isPlatform) {
      loadSessions();
      fetchAllGroups().then(response => setGroups((response.data?.content ?? []) as Group[]));
    }
  }, [userId, isPlatform, loadUser, loadSessions]);

  const memberGroups = useMemo(
    () => groups.filter(group => (group.group_users ?? []).includes(userId)),
    [groups, userId],
  );

  const usersLink = isPlatform ? `${USER_BASE_URL}?scope=platform` : USER_BASE_URL;

  if (notFound) {
    return <SecurityDetailNotFound>{t('This user could not be found.')}</SecurityDetailNotFound>;
  }

  if (!user) {
    return <Loader />;
  }

  const displayName = [user.user_firstname, user.user_lastname].filter(Boolean).join(' ').trim()
    || user.user_email;

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="object"
          elements={[
            { label: t(SETTINGS_LABEL) },
            { label: t('Security') },
            {
              label: t('Users'),
              link: usersLink,
            },
            {
              label: displayName,
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
            icon={PermIdentityOutlined}
            title={displayName}
            chips={(
              <>
                <Chip size="small" variant="outlined" label={user.user_email} sx={{ borderRadius: 1 }} />
                {user.user_admin && (
                  <Chip
                    size="small"
                    color="primary"
                    icon={<VerifiedUserOutlined />}
                    label={t('Administrator')}
                    sx={{ borderRadius: 1 }}
                  />
                )}
              </>
            )}
            action={isPlatform
              ? (
                  <PlatformUserPopover
                    platformUser={user}
                    actions={['Update', 'Update password', 'Delete']}
                    onUpdate={updated => setUser(updated)}
                    onDelete={() => navigate(usersLink)}
                  />
                )
              : (
                  <UserPopover
                    user={user}
                    actions={['Update', 'Update password', 'Delete']}
                    onSubmitUpdate={(data: UserInput) => dispatch(updateUser(userId, data)).then(loadUser)}
                    onSubmitDelete={() => dispatch(deleteUser(userId)).then(() => navigate(usersLink))}
                    onSubmitPassword={(data: ChangePasswordInput) => dispatch(updateUserPassword(userId, data))}
                    permissions={{
                      manage: [ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS],
                      delete: [ACTIONS.DELETE, SUBJECTS.TENANT_SETTINGS],
                    }}
                  />
                )}
          />

          <InformationGrid title={t('Information')}>
            <Field label={t('Email')}>{user.user_email}</Field>
            <Field label={t('First name')}>{user.user_firstname || '-'}</Field>
            <Field label={t('Last name')}>{user.user_lastname || '-'}</Field>
            <Field label={t('Organization')}>{user.user_organization_name || '-'}</Field>
            <Field label={t('Phone number')}>{user.user_phone || '-'}</Field>
            <Field label={t('Administrator')}>{user.user_admin ? t('Yes') : t('No')}</Field>
            <Field label={t('Tags')}>
              <ItemTags variant="list" tags={user.user_tags ?? []} />
            </Field>
          </InformationGrid>

          <DetailSections>
            {!isPlatform && (
              <Section title={t('Groups')}>
                {memberGroups.length === 0
                  ? <Empty message={t('This user does not belong to any group.')} />
                  : (
                      <List disablePadding>
                        {memberGroups.map(group => (
                          <ListItem key={group.group_id} divider disablePadding>
                            <ListItemButton component={Link} to={`${GROUP_BASE_URL}/${group.group_id}`}>
                              <ListItemIcon sx={{ minWidth: 36 }}><GroupsOutlined color="primary" /></ListItemIcon>
                              <ListItemText primary={group.group_name} />
                            </ListItemButton>
                          </ListItem>
                        ))}
                      </List>
                    )}
              </Section>
            )}

            {!isPlatform && (
              <Section title={t('Sessions')}>
                {sessions.length === 0
                  ? <Empty message={t('No active session.')} />
                  : (
                      <SessionsTable
                        sessions={sessions}
                        canManage
                        onKill={sessionId => killSession(sessionId).then(loadSessions)}
                      />
                    )}
              </Section>
            )}

            {(user.user_tenants?.length ?? 0) > 0 && (
              <Section title={t('Tenants')}>
                <List disablePadding>
                  {user.user_tenants?.map(tenant => (
                    <ListItem key={tenant.tenant_id} divider disablePadding>
                      <ListItemText primary={tenant.tenant_name ?? tenant.tenant_id} />
                    </ListItem>
                  ))}
                </List>
              </Section>
            )}
          </DetailSections>
        </Box>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default UserDetail;
