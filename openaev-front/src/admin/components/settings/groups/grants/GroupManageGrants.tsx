import { type FunctionComponent } from 'react';

import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type Group } from '../../../../../utils/api-types';
import GroupManageAtomicTestingGrants from './atomic_testings/GroupManageAtomicTestingGrants';
import GroupManagePayloadGrants from './payloads/GroupManagePayloadGrants';
import GroupManageScenarioGrants from './scenarios/GroupManageScenarioGrants';
import GroupManageSimulationGrants from './simulations/GroupManageSimulationGrants';
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

  return (
    <Drawer
      open={openGrants}
      handleClose={handleCloseGrants}
      title={t('Manage grants')}
    >
      <TabbedView
        tabs={[
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
            key: 'Payloads',
            label: t('Payloads'),
            component: <GroupManagePayloadGrants groupId={group.group_id} onGrantChange={fetchAndUpdateGroup} />,
          },
        ]}
      />
    </Drawer>
  );
};

export default GroupManageGrants;
