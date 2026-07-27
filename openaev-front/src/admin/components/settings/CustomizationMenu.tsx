import { NotificationsOutlined } from '@mui/icons-material';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent, memo } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';

/**
 * Right submenu of Settings > Customization, mirroring OpenCTI's
 * CustomizationMenu (where Notifiers lives under Customization).
 */
const CustomizationMenuComponent: FunctionComponent = () => {
  const entries: RightMenuEntry[] = [
    {
      path: '/admin/settings/customization/asset_rules',
      icon: () => (<SelectGroup />),
      label: 'Default asset rules',
    },
    {
      path: '/admin/settings/customization/notifiers',
      icon: () => (<NotificationsOutlined />),
      label: 'Notifiers',
    },
  ];

  return (
    <RightMenu entries={entries} />
  );
};

const CustomizationMenu = memo(CustomizationMenuComponent);

export default CustomizationMenu;
