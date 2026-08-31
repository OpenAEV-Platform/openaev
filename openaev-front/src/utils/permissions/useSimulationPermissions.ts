import { useMemo } from 'react';

import { type ExercisesHelper } from '../../actions/exercises/exercise-helper';
import { type LoggedHelper, type UserHelper } from '../../actions/helper';
import { useHelper } from '../../store';
import { type SimulationDetails } from '../api-types';
import { useAbility } from './permissionsContext';
import { ACTIONS, SUBJECTS } from './types';

const useSimulationPermissions = (exerciseId: string, fullExercise?: SimulationDetails) => {
  const ability = useAbility();

  const { exercise, me, logged } = useHelper((helper: ExercisesHelper & UserHelper & LoggedHelper) => {
    return {
      exercise: helper.getExercise(exerciseId),
      me: helper.getMe(),
      logged: helper.logged(),
    };
  });

  // Memoized: this result feeds context providers, so it must keep a stable identity
  // when nothing permission-related changed.
  return useMemo(() => {
    if ((!fullExercise && !exercise) || !me) {
      return {
        canAccess: false,
        canManage: false,
        canLaunch: false,
        canDelete: false,
        readOnly: true,
        isLoggedIn: Boolean(logged),
        isRunning: false,
      };
    }

    const canAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, exerciseId) || ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT);
    const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, exerciseId) || ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
    const canLaunch = ability.can(ACTIONS.LAUNCH, SUBJECTS.RESOURCE, exerciseId) || ability.can(ACTIONS.LAUNCH, SUBJECTS.ASSESSMENT);
    const canDelete = ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, exerciseId) || ability.can(ACTIONS.DELETE, SUBJECTS.ASSESSMENT);
    const isRunning = (exercise || fullExercise).exercise_status === 'RUNNING';
    const readOnly = !canManage;

    return {
      canAccess,
      canManage,
      canLaunch,
      canDelete,
      readOnly,
      isLoggedIn: Boolean(logged),
      isRunning,
    };
  }, [ability, exerciseId, exercise, fullExercise, me, logged]);
};

export default useSimulationPermissions;
