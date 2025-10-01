import { CastForEducationOutlined, CastOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { Fragment, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type InjectStore } from '../actions/injects/Inject';
import InjectIcon from '../admin/components/common/injects/InjectIcon';
import { type Inject, type Team } from '../utils/api-types';
import useSearchAnFilter from '../utils/SortingFiltering';
import { truncate } from '../utils/String';
import { splitDuration } from '../utils/Time';
import { isNotEmptyField } from '../utils/utils';
import { useFormatter } from './i18n';

const useStyles = makeStyles()(() => ({
  container: {
    marginTop: 60,
    paddingRight: 40,
  },
  names: {
    float: 'left',
    width: '10%',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  lineName: {
    width: '100%',
    height: 50,
    lineHeight: '50px',
  },
  name: {
    fontSize: 14,
    fontWeight: 400,
    display: 'flex',
    alignItems: 'center',
  },
  timeline: {
    float: 'left',
    width: '90%',
    position: 'relative',
  },
  line: {
    position: 'relative',
    width: '100%',
    height: 50,
    lineHeight: '50px',
    padding: '0 20px 0 20px',
    borderBottom: '1px solid rgba(255, 255, 255, 0.15)',
    verticalAlign: 'middle',
  },
  scale: {
    position: 'absolute',
    width: '100%',
    height: '100%',
    top: 0,
    left: 0,
  },
  tick: {
    position: 'absolute',
    width: 1,
  },
  tickLabelTop: {
    position: 'absolute',
    left: -28,
    top: -20,
    width: 100,
    fontSize: 10,
  },
  tickLabelBottom: {
    position: 'absolute',
    left: -28,
    bottom: -20,
    width: 100,
    fontSize: 10,
  },
  injectGroup: {
    position: 'absolute',
    padding: '6px 5px 0 5px',
    zIndex: 1000,
    display: 'grid',
    gridAutoFlow: 'column',
    gridTemplateRows: 'repeat(2, 20px)',
  },
}));

interface Props {
  injects: InjectStore[];
  teams: Team[];
  onSelectInject: (injectId: string) => void;
}

const Timeline: FunctionComponent<Props> = ({ injects, onSelectInject, teams }) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();

  // Retrieve data
  const getInjectsPerTeam = (teamId: string) => {
    return injects.filter(i => i.inject_teams?.includes(teamId));
  };

  const injectsPerTeam = R.mergeAll(
    teams.map((a: Team) => ({ [a.team_id]: getInjectsPerTeam(a.team_id) })),
  );

  const allTeamInjectIds = new Set(R.values(injectsPerTeam).flat().map((inj: Inject) => inj.inject_id));

  // Build map of technical Injects or without team
  /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
  const injectsWithoutTeamMap = injects.reduce((acc: { [x: string]: any[] }, inject: InjectStore) => {
    /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    let keys: any[] = [];

    if (!allTeamInjectIds.has(inject.inject_id)) {
      if (
        inject.inject_injector_contract?.convertedContent
        && 'fields' in inject.inject_injector_contract.convertedContent
        && inject.inject_injector_contract.convertedContent.fields.some(
          /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
          (field: any) => field.key === 'teams',
        )
      ) {
        keys = ['No teams'];
      } else if (inject.inject_type !== null) {
        keys = [inject.inject_type];
      }
    }

    keys?.forEach((key) => {
      if (!acc[key]) {
        acc[key] = [];
      }
      acc[key].push(inject);
    });

    return acc;
  }, {} as { [key: string]: Inject[] });

  const injectsMap = {
    ...injectsPerTeam,
    ...injectsWithoutTeamMap,
  };

  // Sorted teams
  const teamInjectNames = R.map((key: string) => ({
    team_id: key,
    team_name: key,
  }), R.keys(injectsMap));

  const sortedNativeTeams = R.sortWith(
    [R.ascend(R.prop('team_name'))],
    teams,
  );

  const filteredTeamInject = R.reject(
    (teamInjectName: Team) => R.includes(
      teamInjectName.team_id,
      R.pluck('team_id', sortedNativeTeams),
    ),
    teamInjectNames,
  );

  const sortedTeams = [...filteredTeamInject, ...sortedNativeTeams];

  // Re utilisation of filter and sort hook
  const searchColumns = ['title', 'description', 'content'];
  const filtering = useSearchAnFilter(
    'inject',
    'depends_duration',
    searchColumns,
  );

  const handleSelectInject = (id: string) => onSelectInject(id);

  const lastInject = R.pipe(
    R.sortWith([R.descend(R.prop('inject_depends_duration'))]),
    R.head,
  )(injects);
  const totalDuration = lastInject
    ? lastInject.inject_depends_duration + 3600
    : 60;
  const tickDuration = Math.round(totalDuration / 20);
  const ticks = [...Array(21)].map((_, i) => tickDuration * i);
  // eslint-disable-next-line consistent-return
  const byTick = R.groupBy((inject: InjectStore) => {
    const duration = inject.inject_depends_duration;
    for (const tick of ticks) {
      if (duration < tick) {
        return tick - tickDuration;
      }
    }
  });

  const grid0 = theme.palette.mode === 'light' ? 'rgba(0,0,0,0)' : 'rgba(255,255,255,0)';
  const grid5 = theme.palette.mode === 'light'
    ? 'rgba(0,0,0,0.05)'
    : 'rgba(255,255,255,0.05)';
  const grid25 = theme.palette.mode === 'light'
    ? '1px solid rgba(0, 0, 0, 0.25)'
    : '1px solid rgba(255, 255, 255, 0.25)';
  const grid15 = theme.palette.mode === 'light'
    ? '1px dashed rgba(0, 0, 0, 0.15)'
    : '1px dashed rgba(255, 255, 255, 0.15)';

  return (
    <>
      {injects.length > 0 && sortedTeams.length > 0 ? (
        <div className={classes.container}>
          <div className={classes.names}>
            {sortedTeams.map(team => (
              <div key={team.team_id} className={classes.lineName}>
                <div className={classes.name}>
                  {team.team_name.startsWith('openaev_') ? (
                    <CastOutlined fontSize="small" />
                  ) : (
                    <CastForEducationOutlined fontSize="small" />
                  )}
                  &nbsp;&nbsp;
                  {team.team_name.startsWith('openaev_')
                    ? t(team.team_name)
                    : truncate(team.team_name, 20)}
                </div>
              </div>
            ))}
          </div>
          <div className={classes.timeline}>
            {sortedTeams.map((team, index) => {
              const injectsGroupedByTick = byTick(
                filtering.filterAndSort(injectsMap[team.team_id] ?? []),
              );
              return (
                <div
                  key={team.team_id}
                  className={classes.line}
                  style={{ backgroundColor: index % 2 === 0 ? grid0 : grid5 }}
                >
                  {Object.keys(injectsGroupedByTick).map((key, i) => {
                    const injectGroupPosition = (parseFloat(key) * 100) / totalDuration;
                    return (
                      <div
                        key={i}
                        className={classes.injectGroup}
                        style={{ left: `${injectGroupPosition}%` }}
                      >
                        {injectsGroupedByTick[key].map((inject: InjectStore) => {
                          const duration = splitDuration(inject.inject_depends_duration || 0);
                          const tooltipContent = (
                            <Fragment>
                              {inject.inject_title}
                              <br />
                              <span style={{
                                display: 'block',
                                textAlign: 'center',
                                fontWeight: 'bold',
                              }}
                              >
                                {`${duration.days} ${t('d')}, ${duration.hours} ${t('h')}, ${duration.minutes} ${t('m')}`}
                              </span>
                            </Fragment>
                          );
                          return (
                            <InjectIcon
                              key={inject.inject_id}
                              isPayload={isNotEmptyField(inject.inject_injector_contract?.injector_contract_payload)}
                              type={
                                inject.inject_injector_contract?.injector_contract_payload
                                  ? inject.inject_injector_contract.injector_contract_payload?.payload_collector_type
                                  || inject.inject_injector_contract.injector_contract_payload?.payload_type
                                  : inject.inject_type
                              }
                              onClick={() => handleSelectInject(inject.inject_id)}
                              done={inject.inject_status !== null}
                              disabled={!inject.inject_enabled}
                              size="small"
                              variant="timeline"
                              tooltip={tooltipContent}
                            />
                          );
                        })}
                      </div>
                    );
                  })}
                </div>
              );
            })}
            <div className={classes.scale}>
              {ticks.map((tick, index) => {
                const duration = splitDuration(tick);
                return (
                  <div
                    key={tick}
                    className={classes.tick}
                    style={{
                      left: `${index * 5}%`,
                      height: index % 5 === 0 ? 'calc(100% + 30px)' : '100%',
                      top: index % 5 === 0 ? -15 : 0,
                      borderRight: index % 5 === 0 ? grid25 : grid15,
                    }}
                  >
                    <div className={classes.tickLabelTop}>
                      {index % 5 === 0
                        ? `${duration.days}
                        ${t('d')}, ${duration.hours}
                        ${t('h')}, ${duration.minutes}
                        ${t('m')}`
                        : ''}
                    </div>
                    <div className={classes.tickLabelBottom}>
                      {index % 5 === 0
                        ? `${duration.days}
                        ${t('d')}, ${duration.hours}
                        ${t('h')}, ${duration.minutes}
                        ${t('m')}`
                        : ''}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
};

export default Timeline;
