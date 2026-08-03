import { type FunctionComponent, type ReactNode } from 'react';

import { fetchExerciseDocuments, fetchScenarioDocuments } from '../../../../../actions/documents/documents-actions';
import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { fetchScenarioTeams } from '../../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { TeamContext } from '../../../common/Context';
import teamContextForScenario from '../../../scenarios/scenario/teams/teamContextForScenario';
import teamContextForExercise from '../../../simulations/simulation/teams/teamContextForExercise';
import { type LogicContext } from '../AddComponentButton';

// The reused inject-form widgets (teams, documents) read from TeamContext and the Redux
// documents map. This provider loads the owning scenario/exercise's teams + documents and
// exposes the matching TeamContext so the Configure-action drawer can host the real widgets.

const ScenarioInjectTargetsProvider: FunctionComponent<{
  scenarioId: string;
  children: ReactNode;
}> = ({ scenarioId, children }) => {
  const dispatch = useAppDispatch();
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  useDataLoader(() => {
    dispatch(fetchScenarioTeams(scenarioId));
    dispatch(fetchScenarioDocuments(scenarioId));
  });
  const teamContext = teamContextForScenario(
    scenarioId,
    scenario?.scenario_teams_users,
    scenario?.scenario_all_users_number,
    scenario?.scenario_users_number,
  );
  return <TeamContext.Provider value={teamContext}>{children}</TeamContext.Provider>;
};

const ExerciseInjectTargetsProvider: FunctionComponent<{
  exerciseId: string;
  children: ReactNode;
}> = ({ exerciseId, children }) => {
  const dispatch = useAppDispatch();
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
    dispatch(fetchExerciseDocuments(exerciseId));
  });
  const teamContext = teamContextForExercise(
    exerciseId,
    exercise?.exercise_teams_users,
    exercise?.exercise_all_users_number,
    exercise?.exercise_users_number,
  );
  return <TeamContext.Provider value={teamContext}>{children}</TeamContext.Provider>;
};

interface Props {
  context: LogicContext;
  scenarioId?: string;
  exerciseId?: string;
  children: ReactNode;
}

const InjectTargetsProvider: FunctionComponent<Props> = ({ context, scenarioId, exerciseId, children }) => {
  if (context === 'scenario' && scenarioId) {
    return <ScenarioInjectTargetsProvider scenarioId={scenarioId}>{children}</ScenarioInjectTargetsProvider>;
  }
  if (context === 'simulation' && exerciseId) {
    return <ExerciseInjectTargetsProvider exerciseId={exerciseId}>{children}</ExerciseInjectTargetsProvider>;
  }
  return <>{children}</>;
};

export default InjectTargetsProvider;
