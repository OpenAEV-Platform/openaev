import { type FunctionComponent } from 'react';

import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type Group } from '../../../../../utils/api-types';
import GroupManageAtomicTestingGrants from './atomic_testings/GroupManageAtomicTestingGrants';
import GroupManageOrganizationGrants from './organizations/GroupManageOrganizationGrants';
import GroupManagePayloadGrants from './payloads/GroupManagePayloadGrants';
import GroupManageScenarioGrants from './scenarios/GroupManageScenarioGrants';
import GroupManageSimulationGrants from './simulations/GroupManageSimulationGrants';
import TabbedView from './ui/TabbedView';

interface GroupManageGrantsProps {
  group: Group;
  openGrants: boolean;
  handleCloseGrants: () => void;
}

const GroupManageGrants: FunctionComponent<GroupManageGrantsProps> = ({
  group,
  openGrants,
  handleCloseGrants,
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
            label: t('Scenarios'),
            component: (
              <GroupManageScenarioGrants groupId={group.group_id} />
            ),
          },
          {
            label: t('Simulations'),
            component: (
              <GroupManageSimulationGrants groupId={group.group_id} />
            ),
          },
          {
            label: t('Organizations'),
            component: <GroupManageOrganizationGrants groupId={group.group_id} />,
          },
          {
            label: t('Atomic testings'),
            component: <GroupManageAtomicTestingGrants groupId={group.group_id} />,
          },
          {
            label: t('Payloads'),
            component: <GroupManagePayloadGrants groupId={group.group_id} />,
          },
        ]}
      />
    </Drawer>
  );
};

export default GroupManageGrants;
