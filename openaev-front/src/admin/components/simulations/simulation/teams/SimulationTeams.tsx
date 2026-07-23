import { type FunctionComponent, useContext } from 'react';
import { useParams } from 'react-router';

import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type Team } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import ConfigurationSection from '../../../common/ConfigurationSection';
import { PermissionsContext, TeamContext } from '../../../common/Context';
import ContextualTeams from '../../../components/teams/ContextualTeams';
import UpdateTeams from '../../../components/teams/UpdateTeams';
import teamContextForExercise from './teamContextForExercise';

interface Props { exerciseTeamsUsers: Exercise['exercise_teams_users'] }

const SimulationTeams: FunctionComponent<Props> = ({ exerciseTeamsUsers }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);

  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { teamsStore }: { teamsStore: Team[] } = useHelper((helper: ExercisesHelper) => ({ teamsStore: helper.getExerciseTeams(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
  });

  return (
    <TeamContext.Provider value={teamContextForExercise(exerciseId, exerciseTeamsUsers)}>
      <ConfigurationSection
        title={t('Teams')}
        count={teamsStore.length}
        action={permissions.canManage && (
          <UpdateTeams addedTeamIds={teamsStore.map((team: Team) => team.team_id)} />
        )}
      >
        <div data-testid="teams-list-section">
          <ContextualTeams teams={teamsStore} />
        </div>
      </ConfigurationSection>
    </TeamContext.Provider>
  );
};

export default SimulationTeams;
