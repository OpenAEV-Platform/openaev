import { Checkbox } from '@mui/material';
import { useEffect } from 'react';

import { addGrant, deleteGrant } from '../../../../../../actions/Grant';
import { fetchGroup } from '../../../../../../actions/Group';
import { type GroupHelper } from '../../../../../../actions/group/group-helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type Grant, type GroupGrantInput, type InjectResultOutput } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import { type TableConfig } from '../ui/TableData';

interface AtomicTestingGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}
const useAtomicTestingGrant = ({ groupId, onGrantChange }: AtomicTestingGrantsProps) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const group = useHelper((helper: GroupHelper) => helper.getGroup(groupId));

  useEffect(() => {
    dispatch(fetchGroup(groupId));
  }, [dispatch]);

  if (!group) {
    return { configs: [] };
  }

  useEffect(() => {
    onGrantChange();
  }, [group]);

  const handleGrant = (injectId: string, grantId: string | null, grantName: GroupGrantInput['grant_name'], checked: boolean) => {
    if (checked) {
      const data: GroupGrantInput = {
        grant_name: grantName,
        grant_resource: injectId,
        grant_resource_type: 'ATOMIC_TESTING',
      };
      dispatch(addGrant(group.group_id, data));
    } else {
      dispatch(deleteGrant(group.group_id, grantId));
    }
  };

  const getGrantIds = (inject: InjectResultOutput) => {
    const grants = group.group_grants ?? [];
    const findGrantId = (name: string) => grants
      .find((g: Grant) => g.grant_resource === inject.inject_id && g.grant_name === name)?.grant_id ?? null;
    return {
      observerId: findGrantId('OBSERVER'),
      plannerId: findGrantId('PLANNER'),
      launcherId: findGrantId('LAUNCHER'),
    };
  };

  const configs: TableConfig<InjectResultOutput>[] = [
    {
      label: t('Atomic testing'),
      value: inject => inject.inject_title,
      width: '40%',
      align: 'left',
    },
    {
      label: t('Access'),
      value: (inject) => {
        const { observerId, plannerId, launcherId } = getGrantIds(inject);
        return (
          <Checkbox
            checked={!!(observerId || plannerId || launcherId)}
            disabled={!!(plannerId || launcherId)}
            onChange={(_, checked) => handleGrant(inject.inject_id, observerId, 'OBSERVER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Manage+Delete'),
      value: (inject) => {
        const { plannerId, launcherId } = getGrantIds(inject);
        return (
          <Checkbox
            checked={!!(plannerId || launcherId)}
            disabled={!!launcherId}
            onChange={(_, checked) => handleGrant(inject.inject_id, plannerId, 'PLANNER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Launch'),
      value: (inject) => {
        const { launcherId } = getGrantIds(inject);
        return (
          <Checkbox
            checked={!!launcherId}
            onChange={(_, checked) => handleGrant(inject.inject_id, launcherId, 'LAUNCHER', checked)}
          />
        );
      },
      width: '20%',
    },
  ];

  return { configs };
};

export default useAtomicTestingGrant;
