import { BarChartOutlined, ReorderOutlined, ViewTimelineOutlined } from '@mui/icons-material';
import { GridLegacy, Paper, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import type { AssetGroupsHelper } from '../../../../../actions/asset_groups/assetgroup-helper';
import { fetchExerciseChallenges, fetchScenarioChallenges } from '../../../../../actions/challenge-action';
import { fetchExerciseArticles } from '../../../../../actions/channels/article-action';
import { type ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchSimulationChannels } from '../../../../../actions/channels/channel-action';
import { fetchExerciseDocuments } from '../../../../../actions/documents/documents-actions';
import { fetchExerciseInjectExpectations, fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { type ChallengeHelper } from '../../../../../actions/helper';
import { testInject } from '../../../../../actions/inject_test/simulation-inject-test-actions';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import { fetchVariablesForExercise } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise } from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForExercise from '../../../../../utils/context/endpoint/EndpointContextForExercise';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import {
  ArticleContext,
  ChallengeContext,
  InjectTestContext,
  type InjectTestContextType,
  TeamContext,
  ViewModeContext,
} from '../../../common/Context';
import InjectDistributionByTeam from '../../../common/injects/InjectDistributionByTeam';
import InjectDistributionByType from '../../../common/injects/InjectDistributionByType';
import Injects from '../../../common/injects/Injects';
import articleContextForExercise from '../articles/articleContextForExercise';
import ExerciseDistributionScoreByTeamInPercentage from '../overview/ExerciseDistributionScoreByTeamInPercentage';
import ExerciseDistributionScoreOverTimeByInjectorContract from '../overview/ExerciseDistributionScoreOverTimeByInjectorContract';
import ExerciseDistributionScoreOverTimeByTeam from '../overview/ExerciseDistributionScoreOverTimeByTeam';
import ExerciseDistributionScoreOverTimeByTeamInPercentage from '../overview/ExerciseDistributionScoreOverTimeByTeamInPercentage';
import teamContextForExercise from '../teams/teamContextForExercise';

const useStyles = makeStyles()(() => ({
  paperChart: {
    position: 'relative',
    padding: '0 20px 0 0',
    overflow: 'hidden',
    height: '100%',
  },
}));

const ExerciseInjects: FunctionComponent = () => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const availableButtons = ['chain', 'list', 'distribution'];
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const [viewMode, setViewMode] = useState(() => {
    const storedValue = localStorage.getItem('scenario_or_exercise_view_mode');
    return storedValue === null || !availableButtons.includes(storedValue) ? 'list' : storedValue;
  });

  const handleViewMode = (mode: string) => {
    localStorage.setItem('scenario_or_exercise_view_mode', mode);
    setViewMode(mode);
  };

  const { exercise, teams, articles, variables } = useHelper(
    (helper: ExercisesHelper & ArticlesHelper & ChallengeHelper & VariablesHelper & TeamsHelper) => {
      return {
        exercise: helper.getExercise(exerciseId),
        teams: helper.getTeams(),
        articles: helper.getExerciseArticles(exerciseId),
        variables: helper.getExerciseVariables(exerciseId),
      };
    },
  );
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
    dispatch(fetchVariablesForExercise(exerciseId));
    dispatch(fetchExerciseDocuments(exerciseId));
  });

  const articleContext = articleContextForExercise(exerciseId);
  const teamContext = teamContextForExercise(exerciseId, exercise.exercise_teams_users, exercise.exercise_all_users_number, exercise.exercise_users_number);
  const endpointContext = endpointContextForExercise(exerciseId);
  const challengeContext = { fetchChallenges: () => dispatch(fetchExerciseChallenges(exerciseId)) };

  const injectTestContext: InjectTestContextType = {
    contextId: exerciseId,
    url: `/admin/simulations/${exerciseId}/tests/`,
    testInject: testInject,
  };

  return (
    <ViewModeContext.Provider value={viewMode}>
      {(viewMode === 'list' || viewMode === 'chain') && (
        <ArticleContext.Provider value={articleContext}>
          <TeamContext.Provider value={teamContext}>
            <EndpointContext.Provider value={endpointContext}>
              <ChallengeContext.Provider value={challengeContext}>
                <InjectTestContext.Provider value={injectTestContext}>
                  <Injects
                    setViewMode={handleViewMode}
                    availableButtons={availableButtons}
                    teams={teams}
                    articles={articles}
                    variables={variables}
                    uriVariable={`/admin/simulations/${exerciseId}/definition`}
                  />
                </InjectTestContext.Provider>
              </ChallengeContext.Provider>
            </EndpointContext.Provider>
          </TeamContext.Provider>
        </ArticleContext.Provider>
      )}
      {viewMode === 'distribution' && (
        <div style={{ marginTop: -12 }}>
          <ToggleButtonGroup
            size="small"
            exclusive={true}
            style={{ float: 'right' }}
            aria-label="Change view mode"
          >
            <Tooltip title={t('List view')}>
              <ToggleButton
                value="list"
                onClick={() => handleViewMode('list')}
                selected={false}
                aria-label="List view mode"
              >
                <ReorderOutlined fontSize="small" color="primary" />
              </ToggleButton>
            </Tooltip>
            <Tooltip title={t('Interactive view')}>
              <ToggleButton
                value="chain"
                onClick={() => handleViewMode('chain')}
                selected={false}
                aria-label="Interactive view mode"
              >
                <ViewTimelineOutlined fontSize="small" color="primary" />
              </ToggleButton>
            </Tooltip>
            <Tooltip title={t('Distribution view')}>
              <ToggleButton
                value="distribution"
                onClick={() => handleViewMode('distribution')}
                selected={true}
                aria-label="Distribution view mode"
              >
                <BarChartOutlined fontSize="small" color="inherit" />
              </ToggleButton>
            </Tooltip>
          </ToggleButtonGroup>
          <GridLegacy container spacing={3}>
            <GridLegacy container item spacing={3}>
              <GridLegacy
                item
                xs={6}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of injects by type')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <InjectDistributionByType exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={6}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of injects by team')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <InjectDistributionByTeam exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expectations by inject type')}
                  {' '}
                  (%)
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <ExerciseDistributionScoreByTeamInPercentage exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expected total score by inject type')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <ExerciseDistributionScoreOverTimeByInjectorContract exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expectations by team')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <ExerciseDistributionScoreOverTimeByTeam exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h4">
                  {t('Distribution of expected total score by team')}
                </Typography>
                <Paper variant="outlined" classes={{ root: classes.paperChart }}>
                  <ExerciseDistributionScoreOverTimeByTeamInPercentage exerciseId={exerciseId} />
                </Paper>
              </GridLegacy>
            </GridLegacy>
          </GridLegacy>
        </div>
      )}
    </ViewModeContext.Provider>
  );
};

export default ExerciseInjects;
