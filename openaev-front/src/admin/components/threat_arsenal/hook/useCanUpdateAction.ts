import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

const useCanUpdateAction = (action: Pick<ThreatArsenalAction, 'action_grant_resource_id'>) => {
  const ability = useAbility();
  return ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS)
    || ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, action.action_grant_resource_id);
};

export default useCanUpdateAction;
