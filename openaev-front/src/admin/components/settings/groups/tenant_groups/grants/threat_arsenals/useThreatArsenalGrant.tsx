import { Checkbox } from '@mui/material';

import { addGrant, deleteGrant } from '../../../../../../../actions/Grant';
import { type GroupHelper } from '../../../../../../../actions/group/group-helper';
import { useFormatter } from '../../../../../../../components/i18n';
import { useHelper } from '../../../../../../../store';
import type { Grant, GroupGrantInput, ThreatArsenalAction } from '../../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../../utils/hooks';
import { type TableConfig } from '../ui/TableData';

interface PayloadGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const useThreatArsenalGrant = ({ groupId, onGrantChange }: PayloadGrantsProps) => {
  const { t, tPick } = useFormatter();
  const dispatch = useAppDispatch();
  const group = useHelper((helper: GroupHelper) => helper.getGroup(groupId));

  const handleGrant = (payloadId: string, grantId: string | null, grantName: GroupGrantInput['grant_name'], checked: boolean) => {
    if (!group) {
      return;
    }

    if (checked) {
      const data: GroupGrantInput = {
        grant_name: grantName,
        grant_resource: payloadId,
        grant_resource_type: 'THREAT_ARSENAL',
      };
      dispatch(addGrant(group.group_id, data)).then(onGrantChange);
    } else {
      dispatch(deleteGrant(group.group_id, grantId)).then(onGrantChange);
    }
  };

  const getGrantIds = (action: ThreatArsenalAction) => {
    const grants = group?.group_grants ?? [];
    const findGrantId = (name: string) =>
      grants.find((g: Grant) => g.grant_resource === action.injector_contract_id && g.grant_name === name)?.grant_id ?? null;

    return {
      observerId: findGrantId('OBSERVER'),
      plannerId: findGrantId('PLANNER'),
    };
  };

  const configs: TableConfig<ThreatArsenalAction>[] = [
    {
      label: t('Threat Arsenal'),
      value: action => tPick(action.action_labels),
      width: '40%',
      align: 'left',
    },
    {
      label: t('Access'),
      value: (action) => {
        const { observerId, plannerId } = getGrantIds(action);
        return (
          <Checkbox
            checked={!!(observerId || plannerId)}
            disabled={!!plannerId || !group}
            onChange={(_, checked) => handleGrant(action.injector_contract_id, observerId, 'OBSERVER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Manage+Delete'),
      value: (action) => {
        const { plannerId } = getGrantIds(action);
        return (
          <Checkbox
            checked={!!plannerId}
            disabled={!group}
            onChange={(_, checked) => handleGrant(action.injector_contract_id, plannerId, 'PLANNER', checked)}
          />
        );
      },
      width: '20%',
    },
  ];

  return { configs };
};

export default useThreatArsenalGrant;
