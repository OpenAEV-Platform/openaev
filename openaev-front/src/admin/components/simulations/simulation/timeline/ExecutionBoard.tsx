import { BoltOutlined, HourglassEmptyOutlined, PendingActionsOutlined, TaskAltOutlined } from '@mui/icons-material';
import { Box, LinearProgress, List, ListItem, ListItemButton, Paper, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ComponentType, type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

import { type InjectStore } from '../../../../../actions/injects/Inject';
import { BACK_LABEL, BACK_URI } from '../../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../../components/i18n';
import { type Exercise, type Inject } from '../../../../../utils/api-types';
import { getInjectStatusLabel } from '../../../../../utils/statusLabels';
import { isNotEmptyField } from '../../../../../utils/utils';
import InjectIcon from '../../../common/injects/InjectIcon';
import InjectPopover from '../../../common/injects/InjectPopover';
import InjectStatus from '../../../common/injects/status/InjectStatus';
import { formatRemaining } from './executionTime';

const COLUMN_MIN_HEIGHT = 260;
const COLUMN_MAX_HEIGHT = 460;

const titleSx = {
  fontSize: 13,
  fontWeight: 600,
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
};

const metaSx = {
  fontFamily: 'Consolas, monaco, monospace',
  fontSize: 11,
  color: 'text.secondary',
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
};

// Entrance animation applied to every board row: new items (e.g. an inject
// that just completed) slide in as the SSE store updates.
const rowInSx = {
  'animation': 'execution-board-in 350ms ease-out',
  '@keyframes execution-board-in': {
    from: {
      opacity: 0,
      transform: 'translateY(-6px)',
    },
    to: {
      opacity: 1,
      transform: 'none',
    },
  },
};

const injectIconFor = (inject: InjectStore) => (
  <InjectIcon
    isPayload={isNotEmptyField(inject.inject_injector_contract?.injector_contract_payload)}
    type={
      inject.inject_injector_contract?.injector_contract_payload
        ? inject.inject_injector_contract.injector_contract_payload?.payload_collector_type
        || inject.inject_injector_contract.injector_contract_payload?.payload_type
        : inject.inject_type
    }
    variant="inline"
  />
);

// One lane of the live board: accent-tinted header with a count badge and a
// capped, internally scrollable body.
const BoardColumn = ({ icon: Icon, label, count, accent, children }: {
  icon: ComponentType<{ sx?: object }>;
  label: string;
  count: number;
  accent: string;
  children: ReactNode;
}) => {
  const theme = useTheme();
  return (
    <Paper
      variant="outlined"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        borderRadius: 1,
        minHeight: COLUMN_MIN_HEIGHT,
        maxHeight: COLUMN_MAX_HEIGHT,
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        padding: theme.spacing(1.25, 2),
        borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
        backgroundColor: alpha(accent, 0.05),
        flexShrink: 0,
      }}
      >
        <Icon sx={{
          fontSize: 16,
          color: accent,
        }}
        />
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontWeight: 600,
          fontSize: 11,
          letterSpacing: '0.12em',
          textTransform: 'uppercase',
          color: 'text.secondary',
        }}
        >
          {label}
        </Typography>
        <Box sx={{
          marginLeft: 'auto',
          padding: theme.spacing(0.125, 1),
          borderRadius: 0.5,
          fontFamily: 'Consolas, monaco, monospace',
          fontSize: 11,
          fontWeight: 700,
          color: accent,
          backgroundColor: alpha(accent, 0.12),
        }}
        >
          {count}
        </Box>
      </Box>
      <Box sx={{
        flex: 1,
        overflowY: 'auto',
      }}
      >
        {children}
      </Box>
    </Paper>
  );
};

// Centered tinted-icon empty state for a board lane.
const ColumnPlaceholder = ({ icon: Icon, accent, message }: {
  icon: ComponentType<{ sx?: object }>;
  accent: string;
  message: string;
}) => (
  <Box sx={{
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 1.5,
    height: '100%',
    minHeight: 180,
    padding: 3,
    textAlign: 'center',
  }}
  >
    <Box sx={{
      width: 40,
      height: 40,
      borderRadius: 1,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      color: accent,
      backgroundColor: alpha(accent, 0.1),
    }}
    >
      <Icon sx={{ fontSize: 22 }} />
    </Box>
    <Typography
      variant="body2"
      sx={{
        color: 'text.secondary',
        maxWidth: 260,
      }}
    >
      {message}
    </Typography>
  </Box>
);

interface Props {
  pendingInjects: InjectStore[];
  inFlightInjects: InjectStore[];
  completedInjects: InjectStore[];
  exercise?: Exercise;
  exerciseId: string;
  now: number;
  setSelectedInjectId: (injectId: Inject['inject_id']) => void;
}

// The live execution board: injects flow left to right across three lanes as
// the simulation runs - up next (with live countdowns), in flight (currently
// executing) and completed (with status and timing).
const ExecutionBoard: FunctionComponent<Props> = ({
  pendingInjects,
  inFlightInjects,
  completedInjects,
  exercise,
  exerciseId,
  now,
  setSelectedInjectId,
}) => {
  const theme = useTheme();
  const { t, fndt } = useFormatter();

  const paused = exercise?.exercise_status === 'PAUSED' || exercise?.exercise_status === 'CANCELED';
  const startTime = exercise?.exercise_start_date ? new Date(exercise.exercise_start_date).getTime() : null;

  const injectDetailLink = (inject: InjectStore) =>
    `/admin/simulations/${exerciseId}/injects/${inject.inject_id}?${BACK_LABEL}=${t('Execution')}&${BACK_URI}=/admin/simulations/${exerciseId}/execution/timeline`;

  return (
    <Box sx={{
      display: 'grid',
      gap: 2,
      gridTemplateColumns: {
        xs: 'minmax(0, 1fr)',
        lg: 'repeat(3, minmax(0, 1fr))',
      },
      alignItems: 'stretch',
    }}
    >
      {/* UP NEXT - scheduled injects with a live countdown */}
      <BoardColumn
        icon={PendingActionsOutlined}
        label={t('Up next')}
        count={pendingInjects.length}
        accent={theme.palette.primary.main}
      >
        {pendingInjects.length > 0 ? (
          <List disablePadding>
            {pendingInjects.map((inject) => {
              const fireTime = inject.inject_date ? new Date(inject.inject_date).getTime() : null;
              const remainingSeconds = fireTime !== null ? (fireTime - now) / 1000 : null;
              // Progress toward the fire time, anchored on the simulation start.
              const progress = fireTime !== null && startTime !== null && fireTime > startTime
                ? Math.min(Math.max(((now - startTime) / (fireTime - startTime)) * 100, 0), 100)
                : null;
              return (
                <ListItem
                  key={inject.inject_id}
                  divider
                  disablePadding
                  sx={rowInSx}
                  secondaryAction={(
                    <InjectPopover
                      inject={inject}
                      setSelectedInjectId={setSelectedInjectId}
                      canDone
                      canTriggerNow
                    />
                  )}
                >
                  <ListItemButton
                    dense
                    onClick={() => setSelectedInjectId(inject.inject_id)}
                    sx={{
                      gap: 1.5,
                      alignItems: 'center',
                      paddingRight: 6,
                    }}
                  >
                    <Box sx={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: 30,
                      flexShrink: 0,
                    }}
                    >
                      {injectIconFor(inject)}
                    </Box>
                    <Box sx={{
                      minWidth: 0,
                      flex: 1,
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 0.5,
                    }}
                    >
                      <Typography sx={titleSx}>{inject.inject_title}</Typography>
                      <Box sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1,
                        minWidth: 0,
                      }}
                      >
                        <Typography sx={metaSx}>{fndt(inject.inject_date)}</Typography>
                        {remainingSeconds !== null && (
                          <Typography sx={{
                            ...metaSx,
                            flexShrink: 0,
                            fontWeight: 700,
                            color: paused ? theme.palette.warning.main : theme.palette.primary.main,
                          }}
                          >
                            {(() => {
                              if (paused) return t('Paused');
                              if (remainingSeconds <= 0) return t('Imminent');
                              return t('in {duration}', { duration: formatRemaining(t, remainingSeconds) });
                            })()}
                          </Typography>
                        )}
                      </Box>
                      {progress !== null && !paused && (
                        <Box sx={{
                          position: 'relative',
                          height: 3,
                          borderRadius: 1,
                          overflow: 'hidden',
                          backgroundColor: alpha(theme.palette.text.primary, 0.06),
                        }}
                        >
                          <Box sx={{
                            position: 'absolute',
                            top: 0,
                            bottom: 0,
                            left: 0,
                            width: `${progress}%`,
                            borderRadius: 1,
                            backgroundColor: alpha(theme.palette.primary.main, 0.7),
                            transition: 'width 1s linear',
                          }}
                          />
                        </Box>
                      )}
                    </Box>
                  </ListItemButton>
                </ListItem>
              );
            })}
          </List>
        ) : (
          <ColumnPlaceholder
            icon={HourglassEmptyOutlined}
            accent={theme.palette.primary.main}
            message={t('No upcoming injects.')}
          />
        )}
      </BoardColumn>

      {/* IN FLIGHT - injects currently queuing / executing */}
      <BoardColumn
        icon={BoltOutlined}
        label={t('In flight')}
        count={inFlightInjects.length}
        accent={theme.palette.warning.main}
      >
        {inFlightInjects.length > 0 ? (
          <List disablePadding>
            {inFlightInjects.map(inject => (
              <ListItem
                key={inject.inject_id}
                divider
                disablePadding
                sx={rowInSx}
              >
                <ListItemButton
                  dense
                  component={Link}
                  to={injectDetailLink(inject)}
                  sx={{
                    gap: 1.5,
                    alignItems: 'center',
                  }}
                >
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: 30,
                    flexShrink: 0,
                  }}
                  >
                    {injectIconFor(inject)}
                  </Box>
                  <Box sx={{
                    minWidth: 0,
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 0.5,
                  }}
                  >
                    <Typography sx={titleSx}>{inject.inject_title}</Typography>
                    <Box sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 1,
                      minWidth: 0,
                    }}
                    >
                      <Box sx={{
                        'width': 6,
                        'height': 6,
                        'borderRadius': '50%',
                        'flexShrink': 0,
                        'backgroundColor': theme.palette.warning.main,
                        'animation': 'execution-inflight-pulse 1.4s ease-in-out infinite',
                        '@keyframes execution-inflight-pulse': {
                          '0%, 100%': { opacity: 1 },
                          '50%': { opacity: 0.25 },
                        },
                      }}
                      />
                      <Typography sx={{
                        ...metaSx,
                        fontWeight: 700,
                        color: theme.palette.warning.main,
                      }}
                      >
                        {t(getInjectStatusLabel(inject.inject_status?.status_name))}
                      </Typography>
                      {inject.inject_status?.tracking_sent_date && (
                        <Typography sx={metaSx}>{fndt(inject.inject_status.tracking_sent_date)}</Typography>
                      )}
                    </Box>
                    <LinearProgress
                      color="warning"
                      sx={{
                        height: 3,
                        borderRadius: 1,
                      }}
                    />
                  </Box>
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        ) : (
          <ColumnPlaceholder
            icon={BoltOutlined}
            accent={theme.palette.warning.main}
            message={t('Nothing in flight right now.')}
          />
        )}
      </BoardColumn>

      {/* COMPLETED - processed injects, newest first */}
      <BoardColumn
        icon={TaskAltOutlined}
        label={t('Completed')}
        count={completedInjects.length}
        accent={theme.palette.success.main}
      >
        {completedInjects.length > 0 ? (
          <List disablePadding>
            {completedInjects.map((inject) => {
              const sentDate = inject.inject_status?.tracking_sent_date;
              const endDate = inject.inject_status?.tracking_end_date;
              const durationSeconds = sentDate && endDate
                ? ((new Date(endDate).getTime() - new Date(sentDate).getTime()) / 1000).toFixed(2)
                : null;
              return (
                <ListItem
                  key={inject.inject_id}
                  divider
                  disablePadding
                  sx={rowInSx}
                >
                  <ListItemButton
                    dense
                    component={Link}
                    to={injectDetailLink(inject)}
                    sx={{
                      gap: 1.5,
                      alignItems: 'center',
                    }}
                  >
                    <Box sx={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: 30,
                      flexShrink: 0,
                    }}
                    >
                      {injectIconFor(inject)}
                    </Box>
                    <Box sx={{
                      minWidth: 0,
                      flex: 1,
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 0.5,
                    }}
                    >
                      <Typography sx={titleSx}>{inject.inject_title}</Typography>
                      <Typography sx={metaSx}>
                        {fndt(sentDate ?? inject.inject_status?.tracking_end_date)}
                        {durationSeconds !== null && ` · ${durationSeconds}${t('s')}`}
                      </Typography>
                    </Box>
                    <Box sx={{ flexShrink: 0 }}>
                      <InjectStatus status={inject.inject_status?.status_name} />
                    </Box>
                  </ListItemButton>
                </ListItem>
              );
            })}
          </List>
        ) : (
          <ColumnPlaceholder
            icon={TaskAltOutlined}
            accent={theme.palette.success.main}
            message={t('No injects processed yet.')}
          />
        )}
      </BoardColumn>
    </Box>
  );
};

export default ExecutionBoard;
