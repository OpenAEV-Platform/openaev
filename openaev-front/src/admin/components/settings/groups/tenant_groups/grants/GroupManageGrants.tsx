import { type FunctionComponent, useEffect, useMemo } from 'react';

import { fetchGroup } from '../../../../../../actions/Group';
import Drawer from '../../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../../components/i18n';
import { type Group } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import GroupManageAtomicTestingGrants from './atomic_testings/GroupManageAtomicTestingGrants';
import GroupManageScenarioGrants from './scenarios/GroupManageScenarioGrants';
import GroupManageSimulationGrants from './simulations/GroupManageSimulationGrants';
import GroupManageThreatArsenalGrants from './threat_arsenals/GroupManageThreatArsenalGrants';
import TabbedView from './ui/TabbedView';

interface GroupManageGrantsProps {
  group: Group;
  openGrants: boolean;
  handleCloseGrants: () => void;
  fetchAndUpdateGroup: () => void;
}

const GroupManageGrants: FunctionComponent<GroupManageGrantsProps> = ({
  group,
  openGrants,
  handleCloseGrants,
  fetchAndUpdateGroup,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  useEffect(() => {
    dispatch(fetchGroup(group.group_id));
  }, [group.group_id]);

  const title = t('Manage grants for group: {groupName}', { groupName: group.group_name });

  const tabs = useMemo(() => [
    {
      key: 'Scenarios',
      label: t('Scenarios'),
      component: (
        <GroupManageScenarioGrants groupId={group.group_id} onGrantChange={fetchAndUpdateGroup} />
      ),
    },
    {
      key: 'Simulations',
      label: t('Simulations'),
      component: (
        <GroupManageSimulationGrants groupId={group.group_id} onGrantChange={fetchAndUpdateGroup} />
      ),
    },
    {
      key: 'Atomic testings',
      label: t('Atomic testings'),
      component: <GroupManageAtomicTestingGrants groupId={group.group_id} onGrantChange={fetchAndUpdateGroup} />,
    },
    {
      key: 'Threat Arsenal',
      label: t('Threat Arsenal'),
      component: <GroupManageThreatArsenalGrants groupId={group.group_id} onGrantChange={fetchAndUpdateGroup} />,
    },
  ], [group.group_id, fetchAndUpdateGroup]);

  return (
    <Drawer
      open={openGrants}
      handleClose={handleCloseGrants}
      title={title}
    >
      <TabbedView tabs={tabs} />
    </Drawer>
  );
};

export default GroupManageGrants;
