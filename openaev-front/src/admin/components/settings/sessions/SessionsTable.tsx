import { DeleteOutlined } from '@mui/icons-material';
import { Box, IconButton, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type SessionOutput } from '../../../../utils/api-types';

interface Props {
  sessions: SessionOutput[];
  canManage?: boolean;
  onKill?: (sessionId: string) => void;
}

// Two data columns (last activity / created) plus a fixed action column, so
// every session lines up in a proper table instead of stacked label lines.
const GRID_COLUMNS = 'minmax(0, 1fr) minmax(0, 1fr) 44px';

const HEADER_SX = {
  fontSize: 10.5,
  fontWeight: 600,
  letterSpacing: '0.08em',
  textTransform: 'uppercase' as const,
  color: 'text.secondary',
};

const SessionsTable: FunctionComponent<Props> = ({ sessions, canManage = false, onKill }) => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();

  return (
    <Box>
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: GRID_COLUMNS,
        alignItems: 'center',
        gap: 2,
        paddingInline: 1.5,
        paddingBlock: 1,
      }}
      >
        <Typography sx={HEADER_SX}>{t('Last activity')}</Typography>
        <Typography sx={HEADER_SX}>{t('Created')}</Typography>
        <span />
      </Box>
      {sessions.map(session => (
        <Box
          key={session.session_id}
          sx={{
            display: 'grid',
            gridTemplateColumns: GRID_COLUMNS,
            alignItems: 'center',
            gap: 2,
            paddingInline: 1.5,
            paddingBlock: 1,
            borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.06)}`,
          }}
        >
          <Typography variant="body2" sx={{ fontWeight: 500 }}>
            {session.session_last_access_at ? fldt(session.session_last_access_at) : '-'}
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {session.session_created_at ? fldt(session.session_created_at) : '-'}
          </Typography>
          {canManage && onKill && session.session_id
            ? (
                <Tooltip title={t('Kill session')}>
                  <IconButton
                    edge="end"
                    size="small"
                    color="error"
                    aria-label={t('Kill session')}
                    onClick={() => onKill(session.session_id!)}
                  >
                    <DeleteOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              )
            : <span />}
        </Box>
      ))}
    </Box>
  );
};

export default SessionsTable;
