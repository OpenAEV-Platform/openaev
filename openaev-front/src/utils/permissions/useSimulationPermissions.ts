import { useContext } from 'react';

import { type ExercisesHelper } from '../../actions/exercises/exercise-helper';
import { type LoggedHelper, type UserHelper } from '../../actions/helper';
import { useHelper } from '../../store';
import { type Exercise } from '../api-types';
import { AbilityContext } from './PermissionsProvider';
import { ACTIONS, SUBJECTS } from './types';

const useSimulationPermissions = (exerciseId: string, fullExercise?: Exercise) => {
  const ability = useContext(AbilityContext);

  const { exercise, me, logged } = useHelper((helper: ExercisesHelper & UserHelper & LoggedHelper) => {
    return {
      exercise: helper.getExercise(exerciseId),
      me: helper.getMe(),
      logged: helper.logged(),
    };
  });

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
};

export default useSimulationPermissions;
