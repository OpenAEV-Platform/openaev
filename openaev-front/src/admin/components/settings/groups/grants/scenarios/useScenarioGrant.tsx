import { Checkbox } from '@mui/material';
import { useEffect } from 'react';

import { addGrant, deleteGrant } from '../../../../../../actions/Grant';
import { fetchGroup } from '../../../../../../actions/Group';
import type { GroupHelper } from '../../../../../../actions/group/group-helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type Grant, type GroupGrantInput, type Scenario } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import { type TableConfig } from '../ui/TableData';

interface ScenarioGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const useScenarioGrant = ({ groupId, onGrantChange }: ScenarioGrantsProps) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const group = useHelper((helper: GroupHelper) => helper.getGroup(groupId));

  useEffect(() => {
    dispatch(fetchGroup(groupId));
  }, [dispatch]);

  useEffect(() => {
    if (!group) return;
    onGrantChange();
  }, [group]);

  if (!group) {
    return { configs: [] };
  }

  const handleGrant = (scenarioId: string, grantId: string | null, grantName: GroupGrantInput['grant_name'], checked: boolean) => {
    if (checked) {
      const data: GroupGrantInput = {
        grant_name: grantName,
        grant_resource: scenarioId,
        grant_resource_type: 'SCENARIO',
      };
      dispatch(addGrant(group.group_id, data));
    } else {
      dispatch(deleteGrant(group.group_id, grantId));
    }
  };

  const getGrantIds = (scenario: Scenario) => {
    const grants = group.group_grants ?? [];
    const findGrantId = (name: string) => grants
      .find((g: Grant) => g.grant_resource === scenario.scenario_id && g.grant_name === name)?.grant_id ?? null;
    return {
      observerId: findGrantId('OBSERVER'),
      plannerId: findGrantId('PLANNER'),
      launcherId: findGrantId('LAUNCHER'),
    };
  };

  const configs: TableConfig<Scenario>[] = [
    {
      label: t('Scenario'),
      value: scenario => scenario.scenario_name,
      width: '40%',
      align: 'left',
    },
    {
      label: t('Access'),
      value: (scenario) => {
        const { observerId, plannerId, launcherId } = getGrantIds(scenario);
        return (
          <Checkbox
            checked={!!(observerId || plannerId || launcherId)}
            disabled={!!(plannerId || launcherId)}
            onChange={(_, checked) => handleGrant(scenario.scenario_id, observerId, 'OBSERVER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Manage+Delete'),
      value: (scenario) => {
        const { plannerId, launcherId } = getGrantIds(scenario);
        return (
          <Checkbox
            checked={!!(plannerId || launcherId)}
            disabled={!!launcherId}
            onChange={(_, checked) => handleGrant(scenario.scenario_id, plannerId, 'PLANNER', checked)}
          />
        );
      },
      width: '20%',
    },
    {
      label: t('Launch'),
      value: (scenario) => {
        const { launcherId } = getGrantIds(scenario);
        return (
          <Checkbox
            checked={!!launcherId}
            onChange={(_, checked) => handleGrant(scenario.scenario_id, launcherId, 'LAUNCHER', checked)}
          />
        );
      },
      width: '20%',
    },
  ];

  return { configs };
};

export default useScenarioGrant;
