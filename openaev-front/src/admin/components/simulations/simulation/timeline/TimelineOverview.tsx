import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, Paper, Typography, useTheme } from '@mui/material';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchExerciseChallenges } from '../../../../../actions/challenge-action';
import type { ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchExerciseDocuments } from '../../../../../actions/documents/documents-actions';
import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { fetchExerciseInjects, updateInjectForExercise } from '../../../../../actions/Inject';
import { type InjectStore } from '../../../../../actions/injects/Inject';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { fetchVariablesForExercise } from '../../../../../actions/variables/variable-actions';
import type { VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { BACK_LABEL, BACK_URI } from '../../../../../components/Breadcrumbs';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import ProgressBarCountdown from '../../../../../components/ProgressBarCountdown';
import SearchFilter from '../../../../../components/SearchFilter';
import Timeline from '../../../../../components/Timeline';
import { useHelper } from '../../../../../store';
import { type Exercise, type Inject } from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForExercise from '../../../../../utils/context/endpoint/EndpointContextForExercise';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useSearchAnFilter from '../../../../../utils/SortingFiltering';
import { isNotEmptyField } from '../../../../../utils/utils';
import { ArticleContext, ChallengeContext, TeamContext } from '../../../common/Context';
import TagsFilter from '../../../common/filters/TagsFilter';
import InjectIcon from '../../../common/injects/InjectIcon';
import InjectPopover from '../../../common/injects/InjectPopover';
import UpdateInject from '../../../common/injects/UpdateInject';
import AnimationMenu from '../AnimationMenu';
import articleContextForExercise from '../articles/articleContextForExercise';
import teamContextForExercise from '../teams/teamContextForExercise';
import InjectOverTimeArea from './InjectOverTimeArea';
import InjectOverTimeLine from './InjectOverTimeLine';

const useStyles = makeStyles()(theme => ({
  item: { height: 50 },
  bodyItems: {
    display: 'grid',
    gap: `0px ${theme.spacing(3)}`,
    gridTemplateColumns: '1fr 1fr 1fr',
    alignItems: 'center',
  },
  bodyItem: {
    fontSize: 14,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
}));

const TimelineOverview = () => {
  const { classes } = useStyles();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { t, fndt } = useFormatter();
  const [selectedInjectId, setSelectedInjectId] = useState<string | null>(null);

  const {
    exercise,
    injects,
    teams,
    articles,
    variables,
  } = useHelper((helper: InjectHelper & ExercisesHelper & ArticlesHelper & VariablesHelper) => {
    return {
      exercise: helper.getExercise(exerciseId),
      injects: helper.getExerciseInjects(exerciseId),
      teams: helper.getExerciseTeams(exerciseId),
      articles: helper.getExerciseArticles(exerciseId),
      variables: helper.getExerciseVariables(exerciseId),
    };
  });

  // Fetching Data
  useDataLoader(() => {
    dispatch(fetchExerciseInjects(exerciseId));
    dispatch(fetchExerciseTeams(exerciseId));
    dispatch(fetchVariablesForExercise(exerciseId));
    dispatch(fetchExerciseDocuments(exerciseId));
  });

  // Sort
  const searchColumns = ['title', 'description', 'content'];
  const filtering = useSearchAnFilter(
    'inject',
    'depends_duration',
    searchColumns,
  );

  const isEnable = (inject: InjectStore): boolean => inject.inject_injector_contract?.convertedContent?.config.expose && !!inject.inject_enabled;
  const filteredInjects: InjectStore[] = filtering.filterAndSort(injects.filter((inject: InjectStore) => isEnable(inject)));
  const pendingInjects: InjectStore[] = filtering.filterAndSort(filteredInjects.filter((inject: InjectStore) => inject.inject_status === null));
  const processedInjects: InjectStore[] = filtering.filterAndSort(filteredInjects.filter((i: InjectStore) => i.inject_status !== null));

  const onUpdateInject = async (inject: Inject) => {
    if (selectedInjectId) {
      await dispatch(updateInjectForExercise(exerciseId, selectedInjectId, inject));
    }
  };

  const teamContext = teamContextForExercise(exerciseId, []);
  const articleContext = articleContextForExercise(exerciseId);
  const endpointContext = endpointContextForExercise(exerciseId);
  const challengeContext = { fetchChallenges: () => dispatch(fetchExerciseChallenges(exerciseId)) };

  return (
    <div>
      <AnimationMenu exerciseId={exerciseId} />
      <div>
        <SearchFilter
          variant="small"
          onChange={filtering.handleSearch}
          keyword={filtering.keyword}
        />
        <TagsFilter
          onAddTag={filtering.handleAddTag}
          onRemoveTag={filtering.handleRemoveTag}
          currentTags={filtering.tags}
        />
      </div>
      <Timeline
        injects={filteredInjects}
        teams={teams}
        onSelectInject={(id: string) => setSelectedInjectId(id)}
      />
      <div className="clearfix" />
      <div style={{
        display: 'grid',
        marginTop: 50,
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: '1fr 1fr',
      }}
      >
        <Typography variant="h4">{t('Pending injects')}</Typography>
        <Typography variant="h4">{t('Processed injects')}</Typography>
        <Paper variant="outlined">
          {pendingInjects.length > 0 ? (
            <List style={{ paddingTop: theme.spacing(0) }}>
              {pendingInjects.map((inject: InjectStore) => {
                return (
                  <ListItem
                    key={inject.inject_id}
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
                      classes={{ root: classes.item }}
                      divider
                      onClick={() => setSelectedInjectId(inject.inject_id)}
                    >
                      <ListItemIcon>
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
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div className={classes.bodyItems}>
                            <div
                              className={classes.bodyItem}
                            >
                              {inject.inject_title}
                            </div>
                            <div
                              className={classes.bodyItem}
                            >
                              <ProgressBarCountdown
                                date={inject.inject_date}
                                paused={
                                  exercise?.exercise_status === 'PAUSED'
                                  || exercise?.exercise_status === 'CANCELED'
                                }
                              />
                            </div>
                            <div
                              className={classes.bodyItem}
                              style={{
                                fontFamily: 'Consolas, monaco, monospace',
                                fontSize: 12,
                              }}
                            >
                              {fndt(inject.inject_date)}
                            </div>
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })}
            </List>
          ) : (
            <Empty message={t('No pending injects in this simulation.')} />
          )}
        </Paper>
        <Paper variant="outlined">
          {processedInjects.length > 0 ? (
            <List style={{ paddingTop: 0 }}>
              {processedInjects.map((inject: InjectStore) => (
                <ListItem key={inject.inject_id}>
                  <ListItemButton
                    dense
                    classes={{ root: classes.item }}
                    divider
                    component={Link}
                    to={`/admin/simulations/${exerciseId}/injects/${inject.inject_id}?${BACK_LABEL}=Animation&${BACK_URI}=/admin/simulations/${exerciseId}/animation/timeline`}
                  >
                    <ListItemIcon>
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
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div className={classes.bodyItems}>
                          <div
                            className={classes.bodyItem}
                          >
                            {inject.inject_title}
                          </div>
                          <div
                            className={classes.bodyItem}
                          >
                            <ItemStatus
                              key={inject.inject_id}
                              variant="inList"
                              label={inject.inject_status?.status_name ? t(inject.inject_status.status_name) : 'No Status'}
                              status={inject.inject_status?.status_name}
                            />
                          </div>
                          <div
                            className={classes.bodyItem}
                            style={{
                              fontFamily: 'Consolas, monaco, monospace',
                              fontSize: 12,
                            }}
                          >
                            {fndt(inject.inject_status?.tracking_sent_date)}
                            {' '}
                            {
                              inject.inject_status?.tracking_sent_date && inject.inject_status.tracking_end_date
                              && ((new Date(inject.inject_status.tracking_end_date).getTime() - new Date(inject.inject_status.tracking_sent_date).getTime()) / 1000).toFixed(2)
                            }
                            {t('s')}
                          </div>
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              ))}
            </List>
          ) : (
            <Empty message={t('No processed injects in this simulation.')} />
          )}
        </Paper>
      </div>
      <div style={{
        display: 'grid',
        marginTop: theme.spacing(3),
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: '1fr 1fr',
      }}
      >
        <Typography variant="h4">{t('Sent injects over time')}</Typography>
        <Typography variant="h4">{t('Sent injects over time')}</Typography>
        <Paper variant="outlined">
          <InjectOverTimeArea injects={filteredInjects} />
        </Paper>
        <Paper variant="outlined">
          <InjectOverTimeLine injects={filteredInjects} />
        </Paper>
      </div>
      {selectedInjectId && (
        <ArticleContext.Provider value={articleContext}>
          <TeamContext.Provider value={teamContext}>
            <EndpointContext.Provider value={endpointContext}>
              <ChallengeContext.Provider value={challengeContext}>
                <UpdateInject
                  open
                  handleClose={() => setSelectedInjectId(null)}
                  onUpdateInject={onUpdateInject}
                  injectId={selectedInjectId}
                  isAtomic={false}
                  injects={injects}
                  articlesFromExerciseOrScenario={articles}
                  uriVariable={`/admin/simulations/${exerciseId}/definition`}
                  variablesFromExerciseOrScenario={variables}
                />
              </ChallengeContext.Provider>
            </EndpointContext.Provider>
          </TeamContext.Provider>
        </ArticleContext.Provider>
      )}
    </div>
  );
};

export default TimelineOverview;
