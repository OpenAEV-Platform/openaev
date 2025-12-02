import * as R from 'ramda';
import { useContext } from 'react';

import { getLoggedSelector } from '../../actions/selectors';
import { useSelectorHelper } from '../../store';
import { AbilityContext } from './PermissionsProvider';
import { ACTIONS, SUBJECTS } from './types';

const useScenarioPermissions = (scenarioId: string) => {
  const ability = useContext(AbilityContext);

  const logged = useSelectorHelper(getLoggedSelector);

  const canAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, scenarioId) || ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, scenarioId) || ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const canLaunch = ability.can(ACTIONS.LAUNCH, SUBJECTS.RESOURCE, scenarioId) || ability.can(ACTIONS.LAUNCH, SUBJECTS.ASSESSMENT);
  const canDelete = ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, scenarioId) || ability.can(ACTIONS.DELETE, SUBJECTS.ASSESSMENT);

  return {
    canAccess,
    canManage,
    canLaunch,
    canDelete,
    readOnly: !canManage,
    isLoggedIn: !R.isEmpty(logged),
    isRunning: false,
  };
};

export default useScenarioPermissions;
