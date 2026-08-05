import { AutoAwesome, NotificationsOutlined } from '@mui/icons-material';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent, memo } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { isFeatureEnabled } from '../../../utils/utils';
import EEChip from '../common/entreprise_edition/EEChip';

/**
 * Right submenu of Settings > Customization, mirroring OpenCTI's
 * CustomizationMenu (where Notifiers lives under Customization).
 */
const CustomizationMenuComponent: FunctionComponent = () => {
  const { settings } = useAuth();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  // The autonomous-attack customization is driven by XTM One (the AI brain); show it only when the
  // feature is on and XTM One is connected, matching the launch entry point's own gate.
  const autonomousReady
    = isFeatureEnabled('AUTONOMOUS_ATTACK_PATH')
      && settings.platform_xtm_one_configured === true;

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
    ...(autonomousReady
      ? [{
          path: '/admin/settings/customization/autonomous_attack',
          icon: () => (<AutoAwesome />),
          label: 'Autonomous attack',
          chip: isEnterpriseEdition ? undefined : <EEChip />,
        }]
      : []),
  ];

  return (
    <RightMenu entries={entries} />
  );
};

const CustomizationMenu = memo(CustomizationMenuComponent);

export default CustomizationMenu;
