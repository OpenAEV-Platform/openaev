import { type FunctionComponent, type ReactNode, useCallback, useMemo } from 'react';

import { fetchExerciseDocuments, fetchScenarioDocuments } from '../../../../../actions/documents/documents-actions';
import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { fetchScenarioTeams } from '../../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { findTeams } from '../../../../../actions/teams/team-actions';
import { type Page } from '../../../../../components/common/queryable/Page';
import { buildClientPage, buildEmptyPage } from '../../../../../components/common/queryable/QueryableUtils';
import { useHelper } from '../../../../../store';
import { type ScopeTeamOutput, type SearchPaginationInput, type TeamOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { TeamContext, type TeamContextType } from '../../../common/Context';
import teamContextForScenario from '../../../scenarios/scenario/teams/teamContextForScenario';
import teamContextForExercise from '../../../simulations/simulation/teams/teamContextForExercise';
import { type LogicContext } from '../AddComponentButton';

// The reused inject-form widgets (teams, documents) read from TeamContext and the Redux
// documents map. This provider loads the owning scenario/exercise's teams + documents and
// exposes the matching TeamContext so the Configure-action drawer can host the real widgets.
//
// In a chaining workflow the audience is defined by the workflow SCOPE, not by the
// scenario/exercise audience: teams are chosen "among the scope". The base scenario/exercise
// context is therefore kept for team management (user counts, create/replace), but its
// `searchTeams` is overridden so the "Modify target teams" picker lists exactly the scope's
// allowlisted teams instead of the (usually empty) scenario/exercise audience.

/**
 * Builds a `searchTeams` that resolves the workflow scope's teams into full {@link TeamOutput}
 * (via {@code findTeams}) and applies text search, sort and pagination client-side, so the picker
 * behaves like a real paginated list while being restricted to the scope perimeter.
 */
const useScopeTeamSearch = (validTeams: ScopeTeamOutput[]): TeamContextType['searchTeams'] => {
  const scopeTeamIds = useMemo(
    () => validTeams.map(team => team.team_id).filter((id): id is string => !!id),
    [validTeams],
  );

  return useCallback(
    async (input: SearchPaginationInput): Promise<{ data: Page<TeamOutput> }> => {
      if (scopeTeamIds.length === 0) {
        return buildEmptyPage<TeamOutput>(input);
      }
      const result = await findTeams(scopeTeamIds);
      let items = (result.data ?? []) as TeamOutput[];

      const text = input.textSearch?.trim().toLowerCase();
      if (text) {
        items = items.filter(team => team.team_name?.toLowerCase().includes(text));
      }

      // The picker only exposes team_name as a sortable column, so a single name comparison
      // (honouring the requested direction) covers every sort the list can request.
      const descending = (input.sorts?.[0]?.direction ?? 'ASC').toUpperCase() === 'DESC';
      items = [...items].sort((a, b) => {
        const comparison = (a.team_name ?? '').localeCompare(b.team_name ?? '');
        return descending ? -comparison : comparison;
      });

      return buildClientPage(items, input);
    },
    [scopeTeamIds],
  );
};

const ScenarioInjectTargetsProvider: FunctionComponent<{
  scenarioId: string;
  validTeams: ScopeTeamOutput[];
  children: ReactNode;
}> = ({ scenarioId, validTeams, children }) => {
  const dispatch = useAppDispatch();
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  useDataLoader(() => {
    dispatch(fetchScenarioTeams(scenarioId));
    dispatch(fetchScenarioDocuments(scenarioId));
  });
  const searchTeams = useScopeTeamSearch(validTeams);
  const teamContext = teamContextForScenario(
    scenarioId,
    scenario?.scenario_teams_users,
    scenario?.scenario_all_users_number,
    scenario?.scenario_users_number,
  );
  return (
    <TeamContext.Provider value={{
      ...teamContext,
      searchTeams,
    }}
    >
      {children}
    </TeamContext.Provider>
  );
};

const ExerciseInjectTargetsProvider: FunctionComponent<{
  exerciseId: string;
  validTeams: ScopeTeamOutput[];
  children: ReactNode;
}> = ({ exerciseId, validTeams, children }) => {
  const dispatch = useAppDispatch();
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
    dispatch(fetchExerciseDocuments(exerciseId));
  });
  const searchTeams = useScopeTeamSearch(validTeams);
  const teamContext = teamContextForExercise(
    exerciseId,
    exercise?.exercise_teams_users,
    exercise?.exercise_all_users_number,
    exercise?.exercise_users_number,
  );
  return (
    <TeamContext.Provider value={{
      ...teamContext,
      searchTeams,
    }}
    >
      {children}
    </TeamContext.Provider>
  );
};

interface Props {
  context: LogicContext;
  scenarioId?: string;
  exerciseId?: string;
  validTeams?: ScopeTeamOutput[];
  children: ReactNode;
}

const InjectTargetsProvider: FunctionComponent<Props> = ({ context, scenarioId, exerciseId, validTeams = [], children }) => {
  if (context === 'scenario' && scenarioId) {
    return <ScenarioInjectTargetsProvider scenarioId={scenarioId} validTeams={validTeams}>{children}</ScenarioInjectTargetsProvider>;
  }
  if (context === 'simulation' && exerciseId) {
    return <ExerciseInjectTargetsProvider exerciseId={exerciseId} validTeams={validTeams}>{children}</ExerciseInjectTargetsProvider>;
  }
  return <>{children}</>;
};

export default InjectTargetsProvider;
