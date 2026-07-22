import { ErrorOutlineOutlined, FactCheckOutlined, HourglassEmptyOutlined, InsightsOutlined, PendingActionsOutlined, RocketLaunchOutlined, TaskAltOutlined, TimelineOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemText, Paper, Typography, useTheme } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { type ComponentType, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';

import { fetchExerciseChallenges } from '../../../../../actions/challenge-action';
import type { ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchExerciseDocuments } from '../../../../../actions/documents/documents-actions';
import { fetchExerciseInjectExpectations, fetchExerciseTeams } from '../../../../../actions/Exercise';
import { fetchExerciseExpectationResult } from '../../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { fetchExerciseInjects, updateInjectForExercise } from '../../../../../actions/Inject';
import { type InjectStore } from '../../../../../actions/injects/Inject';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { fetchVariablesForExercise } from '../../../../../actions/variables/variable-actions';
import type { VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { BACK_LABEL, BACK_URI } from '../../../../../components/Breadcrumbs';
import { HeroStat, HeroStats, SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import PostureGauges from '../../../../../components/common/detail/PostureGauges';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import ProgressBarCountdown from '../../../../../components/ProgressBarCountdown';
import SearchFilter from '../../../../../components/SearchFilter';
import Timeline from '../../../../../components/Timeline';
import { useHelper } from '../../../../../store';
import { type Exercise, type ExpectationResultsByType, type Inject, type InjectExpectationOutput } from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForExercise from '../../../../../utils/context/endpoint/EndpointContextForExercise';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../../utils/SortingFiltering';
import { isNotEmptyField } from '../../../../../utils/utils';
import { ArticleContext, ChallengeContext, TeamContext } from '../../../common/Context';
import TagsFilter from '../../../common/filters/TagsFilter';
import InjectIcon from '../../../common/injects/InjectIcon';
import InjectPopover from '../../../common/injects/InjectPopover';
import InjectStatus from '../../../common/injects/status/InjectStatus';
import UpdateInject from '../../../common/injects/UpdateInject';
import articleContextForExercise from '../articles/articleContextForExercise';
import ExecutionMenu from '../ExecutionMenu';
import teamContextForExercise from '../teams/teamContextForExercise';
import InjectOverTimeArea from './InjectOverTimeArea';

// Centered tinted-icon empty state used by every block of the execution
// overview (same anatomy as the simulation overview placeholder).
const ExecutionPlaceholder = ({ icon: Icon, message }: {
  icon: ComponentType<{ sx?: object }>;
  message: string;
}) => {
  const theme = useTheme();
  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 1.5,
      minHeight: 160,
      height: '100%',
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
        color: 'primary.main',
        backgroundColor: alpha(theme.palette.primary.main, 0.1),
      }}
      >
        <Icon sx={{ fontSize: 22 }} />
      </Box>
      <Typography
        variant="body2"
        sx={{
          color: 'text.secondary',
          maxWidth: 420,
        }}
      >
        {message}
      </Typography>
    </Box>
  );
};

// The Execution tab landing screen: a live exposure-validation overview of the
// simulation execution - headline progress metrics, prevention / detection /
// human response posture, the attack timeline, pending vs executed injects and
// the cumulative execution flow.
const ExecutionOverview = () => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { t, fndt } = useFormatter();
  const [selectedInjectId, setSelectedInjectId] = useState<string | null>(null);
  const [results, setResults] = useState<ExpectationResultsByType[] | null>(null);

  const {
    exercise,
    injects,
    teams,
    articles,
    variables,
    injectExpectations,
  } = useHelper((helper: InjectHelper & ExercisesHelper & ArticlesHelper & VariablesHelper) => {
    return {
      exercise: helper.getExercise(exerciseId),
      injects: helper.getExerciseInjects(exerciseId),
      teams: helper.getExerciseTeams(exerciseId),
      articles: helper.getExerciseArticles(exerciseId),
      variables: helper.getExerciseVariables(exerciseId),
      injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
    };
  });

  // Fetching Data
  useDataLoader(() => {
    dispatch(fetchExerciseInjects(exerciseId));
    dispatch(fetchExerciseTeams(exerciseId));
    dispatch(fetchVariablesForExercise(exerciseId));
    dispatch(fetchExerciseDocuments(exerciseId));
    dispatch(fetchExerciseInjectExpectations(exerciseId));
  });

  useEffect(() => {
    fetchExerciseExpectationResult(exerciseId)
      .then((result: { data: ExpectationResultsByType[] }) => setResults(result.data))
      // Degrade to the empty placeholder instead of an infinite loader on failure.
      .catch(() => setResults([]));
  }, [exerciseId]);

  // Sort
  const searchColumns = ['title', 'description', 'content'];
  const filtering = useSearchAndFilter(
    'inject',
    'depends_duration',
    searchColumns,
  );

  const isEnable = (inject: InjectStore): boolean => !!inject.inject_enabled;
  const filteredInjects: InjectStore[] = filtering.filterAndSort(injects.filter((inject: InjectStore) => isEnable(inject)));
  // filteredInjects is already filtered and sorted; a plain partition keeps its order.
  const pendingInjects: InjectStore[] = filteredInjects.filter((inject: InjectStore) => inject.inject_status === null);
  const processedInjects: InjectStore[] = filteredInjects.filter((i: InjectStore) => i.inject_status !== null);

  // Headline metrics computed on the WHOLE simulation (unaffected by the
  // search / tags filters that scope the timeline and lists below).
  const enabledInjects: InjectStore[] = injects.filter((inject: InjectStore) => isEnable(inject));
  const executedCount = enabledInjects.filter((inject: InjectStore) => inject.inject_status !== null).length;
  const errorCount = enabledInjects.filter((inject: InjectStore) => inject.inject_status?.status_name === 'ERROR').length;
  const progress = enabledInjects.length > 0 ? Math.round((executedCount / enabledInjects.length) * 100) : 0;
  const pendingValidations = (injectExpectations ?? []).filter(
    (expectation: InjectExpectationOutput) => expectation.inject_expectation_type === 'MANUAL' && expectation.inject_expectation_status === 'PENDING',
  ).length;

  const onUpdateInject = async (inject: Inject) => {
    if (selectedInjectId) {
      await dispatch(updateInjectForExercise(exerciseId, selectedInjectId, inject));
    }
  };

  const teamContext = teamContextForExercise(exerciseId, []);
  const articleContext = articleContextForExercise(exerciseId);
  const endpointContext = endpointContextForExercise(exerciseId);
  const challengeContext = { fetchChallenges: () => dispatch(fetchExerciseChallenges(exerciseId)) };

  const injectIcon = (inject: InjectStore) => (
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

  return (
    <div>
      <ExecutionMenu exerciseId={exerciseId} />
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        paddingBottom: 5,
      }}
      >
        {/* Headline execution metrics */}
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderRadius: 1,
          }}
        >
          <HeroStats>
            <HeroStat
              icon={RocketLaunchOutlined}
              label={t('Execution progress')}
              value={`${progress}%`}
            />
            <HeroStat
              icon={TaskAltOutlined}
              label={t('Processed injects')}
              value={executedCount}
            />
            <HeroStat
              icon={PendingActionsOutlined}
              label={t('Pending injects')}
              value={enabledInjects.length - executedCount}
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
        </Paper>

        {/* Exposure validation posture */}
        <SectionBlock title={t('Exposure validation')}>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100%',
          }}
          >
            {(() => {
              if (!results) return <Loader variant="inElement" />;
              if (results.length === 0) {
                return (
                  <ExecutionPlaceholder
                    icon={InsightsOutlined}
                    message={t('Prevention, detection and vulnerability results will appear here once the simulation runs.')}
                  />
                );
              }
              return <PostureGauges expectationResultsByTypes={results} humanValidationLink={`/admin/simulations/${exerciseId}/execution/validations`} />;
            })()}
          </Box>
        </SectionBlock>

        {/* Scoping toolbar for the timeline and lists below */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: theme.spacing(1.5),
        }}
        >
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

        {/* Attack timeline */}
        <SectionBlock title={t('Attack timeline')}>
          {filteredInjects.length > 0 ? (
            <Timeline
              injects={filteredInjects}
              teams={teams}
              onSelectInject={(id: string) => setSelectedInjectId(id)}
            />
          ) : (
            <ExecutionPlaceholder
              icon={TimelineOutlined}
              message={t('No injects to display in this simulation.')}
            />
          )}
        </SectionBlock>

        {/* Pending / processed injects */}
        <Box sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: {
            xs: 'minmax(0, 1fr)',
            lg: 'repeat(2, minmax(0, 1fr))',
          },
          alignItems: 'stretch',
        }}
        >
          <SectionBlock title={t('Pending injects')} disablePadding>
            {pendingInjects.length > 0 ? (
              <List disablePadding>
                {pendingInjects.map((inject: InjectStore) => {
                  return (
                    <ListItem
                      key={inject.inject_id}
                      divider
                      disablePadding
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
                      >
                        <Box sx={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          width: 30,
                          flexShrink: 0,
                          marginRight: 1.5,
                        }}
                        >
                          {injectIcon(inject)}
                        </Box>
                        <ListItemText
                          disableTypography
                          primary={(
                            <Box sx={{
                              display: 'grid',
                              gap: 2,
                              gridTemplateColumns: 'minmax(0, 1.2fr) minmax(0, 1fr) minmax(0, 1fr)',
                              alignItems: 'center',
                            }}
                            >
                              <Typography sx={{
                                fontSize: 13.5,
                                fontWeight: 600,
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                              }}
                              >
                                {inject.inject_title}
                              </Typography>
                              <div>
                                <ProgressBarCountdown
                                  date={inject.inject_date}
                                  paused={
                                    exercise?.exercise_status === 'PAUSED'
                                    || exercise?.exercise_status === 'CANCELED'
                                  }
                                />
                              </div>
                              <Typography sx={{
                                fontFamily: 'Consolas, monaco, monospace',
                                fontSize: 12,
                                color: 'text.secondary',
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                              }}
                              >
                                {fndt(inject.inject_date)}
                              </Typography>
                            </Box>
                          )}
                        />
                      </ListItemButton>
                    </ListItem>
                  );
                })}
              </List>
            ) : (
              <ExecutionPlaceholder
                icon={HourglassEmptyOutlined}
                message={t('No pending injects in this simulation.')}
              />
            )}
          </SectionBlock>
          <SectionBlock title={t('Processed injects')} disablePadding>
            {processedInjects.length > 0 ? (
              <List disablePadding>
                {processedInjects.map((inject: InjectStore) => (
                  <ListItem key={inject.inject_id} divider disablePadding>
                    <ListItemButton
                      dense
                      component={Link}
                      to={`/admin/simulations/${exerciseId}/injects/${inject.inject_id}?${BACK_LABEL}=${t('Execution')}&${BACK_URI}=/admin/simulations/${exerciseId}/execution/timeline`}
                    >
                      <Box sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        width: 30,
                        flexShrink: 0,
                        marginRight: 1.5,
                      }}
                      >
                        {injectIcon(inject)}
                      </Box>
                      <ListItemText
                        disableTypography
                        primary={(
                          <Box sx={{
                            display: 'grid',
                            gap: 2,
                            gridTemplateColumns: 'minmax(0, 1.2fr) minmax(0, 1fr) minmax(0, 1fr)',
                            alignItems: 'center',
                          }}
                          >
                            <Typography sx={{
                              fontSize: 13.5,
                              fontWeight: 600,
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                            }}
                            >
                              {inject.inject_title}
                            </Typography>
                            <div>
                              <InjectStatus status={inject.inject_status?.status_name} />
                            </div>
                            <Typography sx={{
                              fontFamily: 'Consolas, monaco, monospace',
                              fontSize: 12,
                              color: 'text.secondary',
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                            }}
                            >
                              {fndt(inject.inject_status?.tracking_sent_date)}
                              {/* Only render the duration (with its unit) when both tracking dates exist. */}
                              {inject.inject_status?.tracking_sent_date && inject.inject_status.tracking_end_date
                                && ` ${((new Date(inject.inject_status.tracking_end_date).getTime() - new Date(inject.inject_status.tracking_sent_date).getTime()) / 1000).toFixed(2)}${t('s')}`}
                            </Typography>
                          </Box>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                ))}
              </List>
            ) : (
              <ExecutionPlaceholder
                icon={TaskAltOutlined}
                message={t('No processed injects in this simulation.')}
              />
            )}
          </SectionBlock>
        </Box>

        {/* Cumulative execution flow */}
        <SectionBlock title={t('Sent injects over time')} disablePadding>
          <InjectOverTimeArea injects={filteredInjects} />
        </SectionBlock>
      </Box>
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
                  uriVariable={`/admin/simulations/${exerciseId}/injects`}
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

export default ExecutionOverview;
