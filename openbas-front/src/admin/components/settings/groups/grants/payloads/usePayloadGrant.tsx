import { Checkbox } from '@mui/material';
import { useEffect } from 'react';

import { addGrant, deleteGrant } from '../../../../../../actions/Grant';
import { fetchGroup } from '../../../../../../actions/Group';
import { type GroupHelper } from '../../../../../../actions/group/group-helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type Grant, type GroupGrantInput, type Payload } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import { type TableConfig } from '../ui/TableData';

const usePayloadGrant = (groupId: string) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const group = useHelper((helper: GroupHelper) => helper.getGroup(groupId));

  useEffect(() => {
    dispatch(fetchGroup(groupId));
  }, [dispatch]);

  if (!group) {
    return { configs: [] };
  }

  const handleGrant = (payloadId: string, grantId: string | null, grantName: GroupGrantInput['grant_name'], checked: boolean) => {
    if (checked) {
      const data: GroupGrantInput = {
        grant_name: grantName,
        grant_resource: payloadId,
        grant_resource_type: 'PAYLOAD',
      };
      dispatch(addGrant(group.group_id, data));
    } else {
      dispatch(deleteGrant(group.group_id, grantId));
    }
  };

  const getGrantIds = (payload: Payload) => {
    const grants = group.group_grants ?? [];
    const findGrantId = (name: string) => grants
      .find((g: Grant) => g.grant_resource === payload.payload_id && g.grant_name === name)?.grant_id ?? null;
    return {
      observerId: findGrantId('OBSERVER'),
      plannerId: findGrantId('PLANNER'),
    };
  };

  const configs: TableConfig<Payload>[] = [
    {
      label: t('Payload'),
      value: payload => payload.payload_name,
      width: '40%',
      align: 'left',
    },
    {
      label: t('Access'),
      value: (payload) => {
        const { observerId, plannerId } = getGrantIds(payload);
        return (
          <Checkbox
            checked={!!(observerId || plannerId)}
            disabled={!!(plannerId)}
            onChange={(_, checked) => handleGrant(payload.payload_id, observerId, 'OBSERVER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Manage+Delete'),
      value: (payload) => {
        const { plannerId } = getGrantIds(payload);
        return (
          <Checkbox
            checked={!!(plannerId)}
            onChange={(_, checked) => handleGrant(payload.payload_id, plannerId, 'PLANNER', checked)}
          />
        );
      },
      width: '20%',
    },
  ];

  return { configs };
};

export default usePayloadGrant;
