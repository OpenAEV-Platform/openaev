import * as R from 'ramda';
import { useMemo } from 'react';

import { type LoggedHelper, type UserHelper } from '../../actions/helper';
import { type ScenariosHelper } from '../../actions/scenarios/scenario-helper';
import { useHelper } from '../../store';
import { useAbility } from './permissionsContext';
import { ACTIONS, SUBJECTS } from './types';

const useScenarioPermissions = (scenarioId: string) => {
  const ability = useAbility();

  const { logged } = useHelper((helper: ScenariosHelper & UserHelper & LoggedHelper) => {
    return { logged: helper.logged() };
  });

  // Memoized: this result feeds context providers, so it must keep a stable identity
  // when nothing permission-related changed.
  return useMemo(() => {
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
  }, [ability, scenarioId, logged]);
};

export default useScenarioPermissions;
