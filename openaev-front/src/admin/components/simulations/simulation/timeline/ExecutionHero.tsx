import { ErrorOutlineOutlined, FactCheckOutlined, PendingActionsOutlined, ScheduleOutlined, TaskAltOutlined } from '@mui/icons-material';
import { Box, Paper, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { HeroStat, HeroStats } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import { type Exercise } from '../../../../../utils/api-types';
import { formatClock, formatRemaining } from './executionTime';

interface Props {
  exercise?: Exercise;
  exerciseId: string;
  totalCount: number;
  completedCount: number;
  inFlightCount: number;
  errorCount: number;
  pendingValidations: number;
  now: number;
  /**
   * Next planned inject (epoch ms), computed by the parent from the injects
   * list. Not read from exercise_next_inject_date: that field only exists on
   * the raw Exercise entity returned by mutations, not on the SimulationDetails
   * DTO of GET /exercises/{id}, so it vanishes after a page reload.
   */
  nextInjectTime?: number | null;
}

// The live header of the Execution screen: a pulsing status beacon, the T+
// elapsed clock, a "next inject" countdown, the headline stats and a full
// width animated progress track. Everything refreshes on the shared 1-second
// tick plus the SSE-driven store updates.
const ExecutionHero: FunctionComponent<Props> = ({
  exercise,
  exerciseId,
  totalCount,
  completedCount,
  inFlightCount,
  errorCount,
  pendingValidations,
  now,
  nextInjectTime = null,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const status = exercise?.exercise_status ?? 'SCHEDULED';
  const running = status === 'RUNNING';
  const statusMeta: Record<Exercise['exercise_status'], {
    label: string;
    color: string;
    pulse?: boolean;
  }> = {
    RUNNING: {
      label: 'Live',
      color: theme.palette.success.main,
      pulse: true,
    },
    PAUSED: {
      label: 'Paused',
      color: theme.palette.warning.main,
    },
    SCHEDULED: {
      label: 'Scheduled',
      color: theme.palette.text.secondary,
    },
    FINISHED: {
      label: 'Finished',
      color: theme.palette.primary.main,
    },
    CANCELED: {
      label: 'Canceled',
      color: theme.palette.text.disabled,
    },
  };
  const meta = statusMeta[status];

  // Elapsed clock: from the start date to now, frozen at the end date once
  // the simulation is over.
  const startTime = exercise?.exercise_start_date ? new Date(exercise.exercise_start_date).getTime() : null;
  const endTime = exercise?.exercise_end_date ? new Date(exercise.exercise_end_date).getTime() : null;
  const clockEnd = (status === 'FINISHED' || status === 'CANCELED') && endTime ? endTime : now;
  const elapsedSeconds = startTime !== null && clockEnd >= startTime ? (clockEnd - startTime) / 1000 : null;

  // Next inject countdown, only meaningful while the simulation runs.
  const nextInjectSeconds = running && nextInjectTime !== null && nextInjectTime >= now ? (nextInjectTime - now) / 1000 : null;

  const progress = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;
  const pendingCount = Math.max(0, totalCount - completedCount - inFlightCount);

  return (
    <Paper
      variant="outlined"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        padding: 2,
        borderRadius: 1,
        background: running
          ? `linear-gradient(135deg, ${alpha(theme.palette.success.main, 0.05)}, transparent 55%)`
          : undefined,
      }}
    >
      {/* Status beacon + elapsed clock + next inject countdown */}
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        flexWrap: 'wrap',
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          padding: theme.spacing(0.5, 1.5),
          borderRadius: 1,
          border: `1px solid ${alpha(meta.color, 0.3)}`,
          backgroundColor: alpha(meta.color, 0.08),
        }}
        >
          <Box sx={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            backgroundColor: meta.color,
            ...(meta.pulse
              ? {
                  'animation': 'execution-beacon 2s ease-out infinite',
                  '@keyframes execution-beacon': {
                    '0%': { boxShadow: `0 0 0 0 ${alpha(meta.color, 0.5)}` },
                    '100%': { boxShadow: `0 0 0 7px ${alpha(meta.color, 0)}` },
                  },
                }
              : {}),
          }}
          />
          <Typography sx={{
            fontFamily: '"Geologica", sans-serif',
            fontWeight: 600,
            fontSize: 11,
            letterSpacing: '0.12em',
            textTransform: 'uppercase',
            color: meta.color,
          }}
          >
            {t(meta.label)}
          </Typography>
        </Box>
        {elapsedSeconds !== null && (
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{
              fontFamily: 'Consolas, monaco, monospace',
              fontSize: 20,
              fontWeight: 600,
              lineHeight: 1.1,
              color: 'text.primary',
            }}
            >
              {`T+${formatClock(elapsedSeconds)}`}
            </Typography>
            <Typography sx={{
              fontSize: 9.5,
              fontWeight: 600,
              letterSpacing: '0.07em',
              textTransform: 'uppercase',
              color: 'text.secondary',
            }}
            >
              {t('Elapsed')}
            </Typography>
          </Box>
        )}
        <div style={{ flex: 1 }} />
        {nextInjectSeconds !== null && (
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            padding: theme.spacing(0.5, 1.5),
            borderRadius: 1,
            border: `1px solid ${alpha(theme.palette.primary.main, 0.25)}`,
            backgroundColor: alpha(theme.palette.primary.main, 0.06),
          }}
          >
            <ScheduleOutlined sx={{
              fontSize: 16,
              color: 'primary.main',
            }}
            />
            <Box>
              <Typography sx={{
                fontSize: 9.5,
                fontWeight: 600,
                letterSpacing: '0.07em',
                textTransform: 'uppercase',
                color: 'text.secondary',
              }}
              >
                {t('Next inject')}
              </Typography>
              <Typography sx={{
                fontFamily: 'Consolas, monaco, monospace',
                fontSize: 13,
                fontWeight: 600,
                lineHeight: 1.1,
                color: 'primary.main',
              }}
              >
                {t('in {duration}', { duration: formatRemaining(t, nextInjectSeconds) })}
              </Typography>
            </Box>
          </Box>
        )}
      </Box>

      {/* Headline stats */}
      <HeroStats spread>
        <HeroStat
          icon={TaskAltOutlined}
          label={t('Processed injects')}
          value={completedCount}
        />
        <HeroStat
          icon={PendingActionsOutlined}
          label={t('Pending injects')}
          value={pendingCount}
        />
        <HeroStat
          icon={ErrorOutlineOutlined}
          label={t('Execution errors')}
          value={errorCount}
          color={errorCount > 0 ? theme.palette.error.main : undefined}
        />
        <HeroStat
          icon={FactCheckOutlined}
          label={t('Pending validations')}
          value={pendingValidations}
          color={pendingValidations > 0 ? theme.palette.warning.main : undefined}
          to={`/admin/simulations/${exerciseId}/execution/validations`}
        />
      </HeroStats>

      {/* Full-width execution progress track */}
      <Box>
        <Box sx={{
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'space-between',
          gap: 2,
          marginBottom: 0.75,
        }}
        >
          <Typography sx={{
            fontSize: 11,
            color: 'text.secondary',
          }}
          >
            {t('{done}/{total} injects processed', {
              done: completedCount,
              total: totalCount,
            })}
          </Typography>
          <Typography sx={{
            fontFamily: '"Geologica", sans-serif',
            fontSize: 15,
            fontWeight: 600,
            color: progress === 100 ? theme.palette.success.main : 'text.primary',
          }}
          >
            {`${progress}%`}
          </Typography>
        </Box>
        <Box sx={{
          position: 'relative',
          height: 6,
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
            overflow: 'hidden',
            background: `linear-gradient(90deg, ${alpha(theme.palette.primary.main, 0.65)}, ${theme.palette.primary.main})`,
            transition: 'width 600ms cubic-bezier(0.22, 1, 0.36, 1)',
            ...(running && progress < 100
              ? {
                  '&::after': {
                    content: '""',
                    position: 'absolute',
                    top: 0,
                    bottom: 0,
                    left: 0,
                    right: 0,
                    background: `linear-gradient(90deg, transparent, ${alpha('#ffffff', 0.3)}, transparent)`,
                    animation: 'execution-progress-shimmer 2.4s ease-in-out infinite',
                  },
                  '@keyframes execution-progress-shimmer': {
                    from: { transform: 'translateX(-100%)' },
                    to: { transform: 'translateX(100%)' },
                  },
                }
              : {}),
          }}
          />
        </Box>
      </Box>
    </Paper>
  );
};

export default ExecutionHero;
