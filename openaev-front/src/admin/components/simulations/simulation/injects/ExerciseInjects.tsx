import { Box, GridLegacy } from '@mui/material';
import { type FunctionComponent, useContext, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { fetchExerciseChallenges } from '../../../../../actions/challenge-action';
import { type ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchExerciseDocuments } from '../../../../../actions/documents/documents-actions';
import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { type ChallengeHelper } from '../../../../../actions/helper';
import { testInject } from '../../../../../actions/inject_test/simulation-inject-test-actions';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import { fetchVariablesForExercise } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import { SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
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
  PermissionsContext,
  TeamContext,
  ViewModeContext,
} from '../../../common/Context';
import InjectDistributionByTeam from '../../../common/injects/InjectDistributionByTeam';
import InjectDistributionByType from '../../../common/injects/InjectDistributionByType';
import Injects from '../../../common/injects/Injects';
import InjectsListButtons from '../../../common/injects/InjectsListButtons';
import articleContextForExercise from '../articles/articleContextForExercise';
import ExerciseDistributionScoreByTeamInPercentage from '../overview/ExerciseDistributionScoreByTeamInPercentage';
import ExerciseDistributionScoreOverTimeByInjectorContract from '../overview/ExerciseDistributionScoreOverTimeByInjectorContract';
import ExerciseDistributionScoreOverTimeByTeam from '../overview/ExerciseDistributionScoreOverTimeByTeam';
import ExerciseDistributionScoreOverTimeByTeamInPercentage from '../overview/ExerciseDistributionScoreOverTimeByTeamInPercentage';
import teamContextForExercise from '../teams/teamContextForExercise';

const ExerciseInjects: FunctionComponent = () => {
  // Standard hooks
  const { t } = useFormatter();
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

  const { permissions } = useContext(PermissionsContext);

  const { exercise, teams, articles, variables } = useHelper(
    (helper: ExercisesHelper & ArticlesHelper & ChallengeHelper & VariablesHelper & TeamsHelper) => {
      return {
        exercise: helper.getExercise(exerciseId),
        teams: helper.getExerciseTeams(exerciseId),
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
  // Stable context identities so the whole injects list does not re-render on each update
  const challengeContext = useMemo(() => ({ fetchChallenges: () => dispatch(fetchExerciseChallenges(exerciseId)) }), [dispatch, exerciseId]);

  const injectTestContext: InjectTestContextType = useMemo(() => ({
    contextId: exerciseId,
    url: `/admin/simulations/${exerciseId}/tests/`,
    testInject: testInject,
  }), [exerciseId]);

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
                    uriVariable={`/admin/simulations/${exerciseId}/injects`}
                  />
                </InjectTestContext.Provider>
              </ChallengeContext.Provider>
            </EndpointContext.Provider>
          </TeamContext.Provider>
        </ArticleContext.Provider>
      )}
      {viewMode === 'distribution' && (
        <div>
          {/* Mirror the exact top-right button group of the list/chain views
              (switcher + Create) so switching modes never moves the buttons
              around. Creation needs the list context, so the button stays
              visible but disabled here. The wrapper reproduces the metrics of
              PaginationComponentV2's top bar (-10px pull-up and the 52px row
              height set by the pagination control) so the buttons sit at the
              exact same pixel position in every mode. */}
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'flex-end',
              alignItems: 'center',
              marginTop: '-10px',
              minHeight: 52,
              marginBottom: 1,
            }}
          >
            <Box display="flex" gap={1} alignItems="center">
              <InjectsListButtons
                availableButtons={availableButtons}
                setViewMode={handleViewMode}
              />
              {permissions.canManage && (
                <ButtonCreate disabled onClick={() => {}} />
              )}
            </Box>
          </Box>
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
                <SectionBlock title={t('Distribution of injects by type')}>
                  <InjectDistributionByType exerciseId={exerciseId} />
                </SectionBlock>
              </GridLegacy>
              <GridLegacy
                item
                xs={6}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <SectionBlock title={t('Distribution of injects by team')}>
                  <InjectDistributionByTeam exerciseId={exerciseId} />
                </SectionBlock>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <SectionBlock title={`${t('Distribution of expectations by inject type')} (%)`}>
                  <ExerciseDistributionScoreByTeamInPercentage exerciseId={exerciseId} />
                </SectionBlock>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <SectionBlock title={t('Distribution of expected total score by inject type')}>
                  <ExerciseDistributionScoreOverTimeByInjectorContract exerciseId={exerciseId} />
                </SectionBlock>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <SectionBlock title={t('Distribution of expectations by team')}>
                  <ExerciseDistributionScoreOverTimeByTeam exerciseId={exerciseId} />
                </SectionBlock>
              </GridLegacy>
              <GridLegacy
                item
                xs={3}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <SectionBlock title={t('Distribution of expected total score by team')}>
                  <ExerciseDistributionScoreOverTimeByTeamInPercentage exerciseId={exerciseId} />
                </SectionBlock>
              </GridLegacy>
            </GridLegacy>
          </GridLegacy>
        </div>
      )}
    </ViewModeContext.Provider>
  );
};

export default ExerciseInjects;
