import { GroupsOutlined, SecurityOutlined } from '@mui/icons-material';
import { type FunctionComponent, memo } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../../components/common/menu/RightMenu';
import { PLATFORM_GROUPS_ROUTE, PLATFORM_ROLES_ROUTE } from './users-capabilities-constants';

const UsersCapabilitiesMenuComponent: FunctionComponent = () => {
  const entries: RightMenuEntry[] = [
    {
      path: PLATFORM_ROLES_ROUTE,
      icon: () => (<SecurityOutlined />),
      label: 'Roles',
    },
    {
      path: PLATFORM_GROUPS_ROUTE,
      icon: () => (<GroupsOutlined />),
      label: 'Groups',
    },
  ];

  return (
    <RightMenu entries={entries} />
  );
};

const UsersCapabilitiesMenu = memo(UsersCapabilitiesMenuComponent);

export default UsersCapabilitiesMenu;
