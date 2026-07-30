import { CastForEducationOutlined, CastOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent, useMemo } from 'react';

import { type InjectStore } from '../../../../../actions/injects/Inject';
import { useFormatter } from '../../../../../components/i18n';
import { type Team } from '../../../../../utils/api-types';
import { truncate } from '../../../../../utils/String';
import { isNotEmptyField } from '../../../../../utils/utils';
import InjectIcon from '../../../common/injects/InjectIcon';
import { formatOffset } from './executionTime';

const AXIS_HEIGHT = 26;
const LANE_HEIGHT = 48;
const LABEL_COLUMN_WIDTH = 170;
// 21 ticks (0..100% by 5%), one major (labelled, solid) tick every 5 minors.
const TICK_STEP_PERCENT = 5;
const MAJOR_TICK_EVERY = 5;

interface Lane {
  id: string;
  name: string;
  technical: boolean;
  injects: InjectStore[];
}

interface Props {
  injects: InjectStore[];
  teams: Team[];
  onSelectInject: (injectId: string) => void;
  /** Simulation start (ISO), anchors the live "now" cursor. */
  startDate?: string;
  /** Whether the simulation is currently RUNNING (shows the now cursor). */
  running?: boolean;
  /** Shared 1-second tick (epoch ms). */
  now: number;
}

// The modernized attack timeline: one lane per team (plus technical lanes for
// team-less injects), inject markers positioned at their planned offset, a
// single top time axis and - while the simulation runs - an animated "now"
// cursor sweeping across the planned schedule.
const AttackTimeline: FunctionComponent<Props> = ({
  injects,
  teams,
  onSelectInject,
  startDate,
  running = false,
  now,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();

  // One lane per team; injects targeting no team fall into technical lanes
  // keyed by their injector type (or a generic "No teams" lane when the
  // contract supports teams but none is set).
  const lanes: Lane[] = useMemo(() => {
    const byTeam = new Map<string, InjectStore[]>();
    teams.forEach((team) => {
      byTeam.set(
        team.team_id,
        injects.filter(inject => inject.inject_teams?.includes(team.team_id) || inject.inject_all_teams),
      );
    });
    const teamInjectIds = new Set([...byTeam.values()].flat().map(inject => inject.inject_id));
    const technical = new Map<string, InjectStore[]>();
    injects.forEach((inject) => {
      if (teamInjectIds.has(inject.inject_id)) {
        return;
      }
      const convertedContent = inject.inject_injector_contract?.convertedContent;
      const supportsTeams = !!convertedContent
        && 'fields' in convertedContent
        && convertedContent.fields.some((field: { key: string }) => field.key === 'teams');
      const key = supportsTeams ? 'No teams' : inject.inject_type;
      if (!key) {
        return;
      }
      technical.set(key, [...(technical.get(key) ?? []), inject]);
    });
    const sortedTeams = [...teams].sort((a, b) => a.team_name.localeCompare(b.team_name));
    return [
      ...[...technical.entries()].map(([key, laneInjects]) => ({
        id: key,
        name: key,
        technical: true,
        injects: laneInjects,
      })),
      ...sortedTeams.map(team => ({
        id: team.team_id,
        name: team.team_name,
        technical: false,
        injects: byTeam.get(team.team_id) ?? [],
      })),
    ];
  }, [injects, teams]);

  // Planned window: latest inject offset plus an hour of padding, split into
  // 21 ticks. Near-simultaneous injects are bucketed on the same tick.
  const totalDuration = useMemo(() => {
    const maxDuration = Math.max(0, ...injects.map(inject => inject.inject_depends_duration ?? 0));
    return maxDuration > 0 ? maxDuration + 3600 : 60;
  }, [injects]);
  const tickCount = 100 / TICK_STEP_PERCENT;
  const tickDuration = Math.round(totalDuration / tickCount);
  const ticks = [...Array(tickCount + 1)].map((_, index) => tickDuration * index);
  const bucketFor = (duration: number): number => {
    for (const tick of ticks) {
      if (duration < tick) {
        return tick - tickDuration;
      }
    }
    return ticks[ticks.length - 1];
  };

  // Live cursor position (percent of the planned window), clamped to the
  // right edge once the wall clock passes the last planned inject.
  const nowPosition = useMemo(() => {
    if (!running || !startDate) {
      return null;
    }
    const elapsedSeconds = (now - new Date(startDate).getTime()) / 1000;
    if (elapsedSeconds < 0) {
      return null;
    }
    return Math.min((elapsedSeconds / totalDuration) * 100, 100);
  }, [running, startDate, now, totalDuration]);

  if (injects.length === 0 || lanes.length === 0) {
    return null;
  }

  const laneLabel = (lane: Lane) => (lane.name.startsWith('openaev_') ? t(lane.name) : truncate(lane.name, 20));

  return (
    <Box sx={{ display: 'flex' }}>
      {/* Lane labels */}
      <Box sx={{
        width: LABEL_COLUMN_WIDTH,
        flexShrink: 0,
      }}
      >
        <div style={{ height: AXIS_HEIGHT }} />
        {lanes.map(lane => (
          <Box
            key={lane.id}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              height: LANE_HEIGHT,
              paddingRight: 1.5,
              minWidth: 0,
            }}
          >
            {lane.name.startsWith('openaev_') || lane.technical
              ? (
                  <CastOutlined sx={{
                    fontSize: 16,
                    color: 'text.secondary',
                    flexShrink: 0,
                  }}
                  />
                )
              : (
                  <CastForEducationOutlined sx={{
                    fontSize: 16,
                    color: 'text.secondary',
                    flexShrink: 0,
                  }}
                  />
                )}
            <Typography sx={{
              fontSize: 13,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
            >
              {laneLabel(lane)}
            </Typography>
          </Box>
        ))}
      </Box>

      {/* Plot area: axis + grid + lanes + markers + now cursor */}
      <Box sx={{
        flex: 1,
        position: 'relative',
        minWidth: 0,
      }}
      >
        {/* Time axis labels (majors only, single row on top) */}
        <Box sx={{
          position: 'relative',
          height: AXIS_HEIGHT,
        }}
        >
          {ticks.map((tick, index) => {
            if (index % MAJOR_TICK_EVERY !== 0) {
              return null;
            }
            // Edge labels are anchored inward so they never overflow the card.
            const transform = (() => {
              if (index === 0) return 'none';
              if (index === ticks.length - 1) return 'translateX(-100%)';
              return 'translateX(-50%)';
            })();
            return (
              <Typography
                key={tick}
                sx={{
                  position: 'absolute',
                  top: 2,
                  left: `${index * TICK_STEP_PERCENT}%`,
                  transform,
                  fontFamily: 'Consolas, monaco, monospace',
                  fontSize: 10,
                  color: 'text.secondary',
                  whiteSpace: 'nowrap',
                }}
              >
                {formatOffset(t, tick)}
              </Typography>
            );
          })}
        </Box>

        {/* Vertical grid */}
        {ticks.map((tick, index) => (
          <Box
            key={tick}
            sx={{
              position: 'absolute',
              top: AXIS_HEIGHT,
              bottom: 0,
              left: `${index * TICK_STEP_PERCENT}%`,
              width: 0,
              borderRight: index % MAJOR_TICK_EVERY === 0
                ? `1px solid ${alpha(theme.palette.text.primary, 0.12)}`
                : `1px dashed ${alpha(theme.palette.text.primary, 0.05)}`,
            }}
          />
        ))}

        {/* Team lanes with their inject markers */}
        {lanes.map((lane, laneIndex) => {
          const groups = new Map<number, InjectStore[]>();
          lane.injects.forEach((inject) => {
            const bucket = bucketFor(inject.inject_depends_duration ?? 0);
            groups.set(bucket, [...(groups.get(bucket) ?? []), inject]);
          });
          return (
            <Box
              key={lane.id}
              sx={{
                'position': 'relative',
                'height': LANE_HEIGHT,
                'backgroundColor': laneIndex % 2 === 1 ? alpha(theme.palette.text.primary, 0.02) : 'transparent',
                'borderBottom': `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
                'transition': 'background-color 120ms',
                '&:hover': { backgroundColor: theme.palette.action.hover },
              }}
            >
              {[...groups.entries()].map(([bucket, groupInjects]) => (
                <Box
                  key={bucket}
                  sx={{
                    position: 'absolute',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    left: `${(bucket * 100) / totalDuration}%`,
                    display: 'grid',
                    gridAutoFlow: 'column',
                    gridTemplateRows: 'repeat(2, 20px)',
                    alignItems: 'center',
                    padding: theme.spacing(0, 0.5),
                    zIndex: 3,
                  }}
                >
                  {groupInjects.map(inject => (
                    <InjectIcon
                      key={inject.inject_id}
                      isPayload={isNotEmptyField(inject.inject_injector_contract?.injector_contract_payload)}
                      type={
                        inject.inject_injector_contract?.injector_contract_payload
                          ? inject.inject_injector_contract.injector_contract_payload?.payload_collector_type
                          || inject.inject_injector_contract.injector_contract_payload?.payload_type
                          : inject.inject_type
                      }
                      onClick={() => onSelectInject(inject.inject_id)}
                      done={inject.inject_status !== null}
                      disabled={!inject.inject_enabled}
                      size="small"
                      variant="timeline"
                      tooltip={(
                        <Fragment>
                          {inject.inject_title}
                          <br />
                          <span style={{
                            display: 'block',
                            textAlign: 'center',
                            fontWeight: 'bold',
                          }}
                          >
                            {formatOffset(t, inject.inject_depends_duration ?? 0)}
                          </span>
                        </Fragment>
                      )}
                    />
                  ))}
                </Box>
              ))}
            </Box>
          );
        })}

        {/* Live "now" cursor */}
        {nowPosition !== null && (
          <Box sx={{
            position: 'absolute',
            top: AXIS_HEIGHT - 6,
            bottom: 0,
            left: `${nowPosition}%`,
            width: '2px',
            zIndex: 4,
            pointerEvents: 'none',
            background: `linear-gradient(180deg, ${theme.palette.primary.main}, ${alpha(theme.palette.primary.main, 0.1)})`,
            boxShadow: `0 0 8px ${alpha(theme.palette.primary.main, 0.5)}`,
            transition: 'left 1s linear',
          }}
          >
            <Box sx={{
              'position': 'absolute',
              'top': -3,
              'left': '50%',
              'transform': 'translateX(-50%)',
              'width': 8,
              'height': 8,
              'borderRadius': '50%',
              'backgroundColor': 'primary.main',
              'animation': 'execution-now-pulse 2s ease-out infinite',
              '@keyframes execution-now-pulse': {
                '0%': { boxShadow: `0 0 0 0 ${alpha(theme.palette.primary.main, 0.5)}` },
                '100%': { boxShadow: `0 0 0 8px ${alpha(theme.palette.primary.main, 0)}` },
              },
            }}
            />
          </Box>
        )}
      </Box>
    </Box>
  );
};

export default AttackTimeline;
