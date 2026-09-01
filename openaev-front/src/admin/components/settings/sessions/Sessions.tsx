import { DeleteOutlined, PersonOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  Paper,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';

import {
  fetchPlatformSessions,
  fetchSessions,
  killPlatformSession,
  killPlatformUserSessions,
  killSession,
  killUserSessions,
} from '../../../../actions/sessions/session-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type SessionOutput } from '../../../../utils/api-types';
import NoAccess from '../../../../utils/permissions/NoAccess';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import useSecurityScope from '../useSecurityScope';
import SessionsTable from './SessionsTable';

const userLabel = (session: SessionOutput | undefined, userId: string): string =>
  session?.session_user_name || userId;

const Sessions = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { scope, canAccessSession } = useSecurityScope();
  const isPlatform = scope === 'PLATFORM';
  const canManage = canAccessSession(scope);

  const [sessions, setSessions] = useState<SessionOutput[] | null>(null);

  const loadSessions = () => {
    if (!canManage) {
      setSessions([]);
      return;
    }
    (isPlatform ? fetchPlatformSessions() : fetchSessions())
      .then(response => setSessions(response.data as SessionOutput[]))
      // Never leave the page on an infinite loader if the request fails.
      .catch(() => setSessions([]));
  };
  useEffect(() => {
    setSessions(null);
    loadSessions();
  }, [isPlatform]);

  const sessionsByUser = useMemo(() => {
    const grouped = new Map<string, SessionOutput[]>();
    (sessions ?? []).forEach((session) => {
      const key = session.session_user_id ?? '-';
      const list = grouped.get(key) ?? [];
      list.push(session);
      grouped.set(key, list);
    });
    return Array.from(grouped.entries()).sort(([keyA, sessionsA], [keyB, sessionsB]) =>
      userLabel(sessionsA[0], keyA).localeCompare(userLabel(sessionsB[0], keyB)));
  }, [sessions]);

  const onKillSession = (sessionId: string) => {
    (isPlatform ? killPlatformSession(sessionId) : killSession(sessionId)).then(() => loadSessions());
  };
  const onKillUserSessions = (userId: string) => {
    (isPlatform ? killPlatformUserSessions(userId) : killUserSessions(userId)).then(() => loadSessions());
  };

  if (!canManage) {
    return <NoAccess />;
  }

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
                    {userLabel(userSessions[0], userId)}
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
