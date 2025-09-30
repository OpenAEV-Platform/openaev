import { Checkbox } from '@mui/material';
import { useEffect } from 'react';

import { addGrant, deleteGrant } from '../../../../../../actions/Grant';
import { fetchGroup } from '../../../../../../actions/Group';
import type { GroupHelper } from '../../../../../../actions/group/group-helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type Exercise, type Grant, type GroupGrantInput } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import type { TableConfig } from '../ui/TableData';

interface SimulationGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const useSimulationGrant = ({ groupId, onGrantChange }: SimulationGrantsProps) => {
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

  const handleGrant = (exerciseId: string, grantId: string | null, grantName: GroupGrantInput['grant_name'], checked: boolean) => {
    if (checked) {
      const data: GroupGrantInput = {
        grant_name: grantName,
        grant_resource: exerciseId,
        grant_resource_type: 'SIMULATION',
      };
      dispatch(addGrant(group.group_id, data));
    } else {
      dispatch(deleteGrant(group.group_id, grantId));
    }
  };

  const getGrantIds = (exercise: Exercise) => {
    const grants = group.group_grants ?? [];
    const findGrantId = (name: string) => grants
      .find((g: Grant) => g.grant_resource === exercise.exercise_id && g.grant_name === name)?.grant_id ?? null;
    return {
      observerId: findGrantId('OBSERVER'),
      plannerId: findGrantId('PLANNER'),
      launcherId: findGrantId('LAUNCHER'),
    };
  };

  const configs: TableConfig<Exercise>[] = [
    {
      label: t('Simulation'),
      value: exercise => exercise.exercise_name,
      width: '40%',
      align: 'left',
    },
    {
      label: t('Access'),
      value: (exercise) => {
        const { observerId, plannerId, launcherId } = getGrantIds(exercise);
        return (
          <Checkbox
            checked={!!(observerId || plannerId || launcherId)}
            disabled={!!(plannerId || launcherId)}
            onChange={(_, checked) => handleGrant(exercise.exercise_id, observerId, 'OBSERVER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Manage+Delete'),
      value: (exercise) => {
        const { plannerId, launcherId } = getGrantIds(exercise);
        return (
          <Checkbox
            checked={!!(plannerId || launcherId)}
            disabled={!!launcherId}
            onChange={(_, checked) => handleGrant(exercise.exercise_id, plannerId, 'PLANNER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Launch'),
      value: (exercise) => {
        const { launcherId } = getGrantIds(exercise);
        return (
          <Checkbox
            checked={!!launcherId}
            onChange={(_, checked) => handleGrant(exercise.exercise_id, launcherId, 'LAUNCHER', checked)}
          />
        );
      },
      width: '20%',
    },
  ];

  return { configs };
};

export default useSimulationGrant;
