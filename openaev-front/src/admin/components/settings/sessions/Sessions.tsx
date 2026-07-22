import { DeleteOutlined, PersonOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  Paper,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useContext, useEffect, useMemo, useState } from 'react';

import type { UserHelper } from '../../../../actions/helper';
import { findPlatformUsers } from '../../../../actions/platform/users/platform-user-action';
import {
  fetchPlatformSessions,
  fetchSessions,
  killPlatformSession,
  killPlatformUserSessions,
  killSession,
  killUserSessions,
} from '../../../../actions/sessions/session-actions';
import { fetchUsers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { useHelper } from '../../../../store';
import { type SessionOutput, type UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import useSecurityScope from '../useSecurityScope';
import SessionsTable from './SessionsTable';

interface NamedUser {
  user_firstname?: string;
  user_lastname?: string;
  user_email?: string;
}

const userLabel = (user: NamedUser | undefined, userId: string): string => {
  if (!user) return userId;
  const name = [user.user_firstname, user.user_lastname].filter(Boolean).join(' ').trim();
  return name || user.user_email || userId;
};

const Sessions = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { scope } = useSecurityScope();
  const isPlatform = scope === 'platform';
  const canManage = ability.can(
    ACTIONS.MANAGE,
    isPlatform ? SUBJECTS.PLATFORM_SETTINGS : SUBJECTS.TENANT_SETTINGS,
  );

  const [sessions, setSessions] = useState<SessionOutput[] | null>(null);
  // Platform sessions can reference users outside the current tenant, so their
  // names are resolved on demand rather than from the tenant users helper.
  const [platformUsersMap, setPlatformUsersMap] = useState<Record<string, UserOutput>>({});

  const { usersMap } = useHelper((helper: UserHelper) => ({ usersMap: helper.getUsersMap() }));
  useDataLoader(() => {
    dispatch(fetchUsers());
  });

  const loadSessions = () => {
    const request = isPlatform ? fetchPlatformSessions() : fetchSessions();
    request
      .then((response) => {
        const result = response.data as SessionOutput[];
        setSessions(result);
        if (isPlatform) {
          const ids = Array.from(
            new Set(result.map(session => session.session_user_id).filter((id): id is string => !!id)),
          );
          if (ids.length > 0) {
            findPlatformUsers(ids).then((usersResponse) => {
              const map: Record<string, UserOutput> = {};
              ((usersResponse.data ?? []) as UserOutput[]).forEach((user) => {
                map[user.user_id] = user;
              });
              setPlatformUsersMap(map);
            });
          }
        }
      })
      // Never leave the page on an infinite loader if the request fails.
      .catch(() => setSessions([]));
  };
  useEffect(() => {
    setSessions(null);
    setPlatformUsersMap({});
    loadSessions();
  }, [isPlatform]);

  const resolveName = (userId: string): string =>
    userLabel(isPlatform ? platformUsersMap[userId] : usersMap[userId], userId);

  const sessionsByUser = useMemo(() => {
    const grouped = new Map<string, SessionOutput[]>();
    (sessions ?? []).forEach((session) => {
      const key = session.session_user_id ?? '-';
      const list = grouped.get(key) ?? [];
      list.push(session);
      grouped.set(key, list);
    });
    return Array.from(grouped.entries()).sort(([a], [b]) =>
      resolveName(a).localeCompare(resolveName(b)));
  }, [sessions, usersMap, platformUsersMap, isPlatform]);

  const onKillSession = (sessionId: string) => {
    (isPlatform ? killPlatformSession(sessionId) : killSession(sessionId)).then(() => loadSessions());
  };
  const onKillUserSessions = (userId: string) => {
    (isPlatform ? killPlatformUserSessions(userId) : killUserSessions(userId)).then(() => loadSessions());
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Security') }, { label: isPlatform ? t('Platform') : t('This tenant') }, {
            label: t('Sessions'),
            current: true,
          }]}
        />
        {sessions === null && <Loader variant="inElement" />}
        {sessions !== null && sessionsByUser.length === 0 && (
          <Empty message={t('No active session.')} />
        )}
        {sessions !== null && sessionsByUser.length > 0 && (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
          >
            {sessionsByUser.map(([userId, userSessions]) => (
              <Paper
                key={userId}
                variant="outlined"
                sx={{ borderRadius: 1 }}
              >
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  padding: 1.5,
                  borderBottom: `1px solid ${theme.palette.divider}`,
                  backgroundColor: alpha(theme.palette.background.paper, 0.6),
                }}
                >
                  <PersonOutlined fontSize="small" color="primary" />
                  <Typography sx={{
                    fontWeight: 600,
                    flex: 1,
                  }}
                  >
                    {resolveName(userId)}
                  </Typography>
                  {canManage && userSessions.length > 1 && (
                    <Button
                      size="small"
                      color="error"
                      variant="outlined"
                      startIcon={<DeleteOutlined fontSize="small" />}
                      onClick={() => onKillUserSessions(userId)}
                    >
                      {t('Kill all sessions')}
                    </Button>
                  )}
                </Box>
                <SessionsTable
                  sessions={userSessions}
                  canManage={canManage}
                  onKill={onKillSession}
                />
              </Paper>
            ))}
          </Box>
        )}
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Sessions;
