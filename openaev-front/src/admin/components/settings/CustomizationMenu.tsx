import { AutoAwesome, DnsOutlined, NotificationsOutlined, SchoolOutlined } from '@mui/icons-material';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent, memo, useContext } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';
import { LESSONS_TEMPLATES_BASE_URL } from '../../../constants/BaseUrls';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../utils/utils';
import EEChip from '../common/entreprise_edition/EEChip';

/**
 * Right submenu of Settings > Customization, mirroring OpenCTI's
 * CustomizationMenu (where Notifiers lives under Customization).
 */
const CustomizationMenuComponent: FunctionComponent = () => {
  const { settings } = useAuth();
  const ability = useContext(AbilityContext);
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  // The autonomous-attack customization is driven by XTM One (the AI brain); show it only when the
  // chaining feature is on (autonomy is a launch mode of chained scenarios, no dedicated flag) and
  // XTM One is connected, matching the launch entry point's own gate.
  const autonomousReady
    = isFeatureEnabled('INJECT_CHAINING')
      && settings.platform_xtm_one_configured === true;

  const entries: RightMenuEntry[] = [
    {
      path: '/admin/settings/customization/asset_rules',
      icon: () => (<SelectGroup />),
      label: 'Default asset rules',
    },
    {
      path: '/admin/settings/customization/custom_domains',
      icon: () => (<DnsOutlined />),
      label: 'Custom domains',
    },
    {
      path: '/admin/settings/customization/notifiers',
      icon: () => (<NotificationsOutlined />),
      label: 'Notifiers',
    },
    ...(ability.can(ACTIONS.ACCESS, SUBJECTS.LESSONS_LEARNED)
      ? [{
          path: LESSONS_TEMPLATES_BASE_URL,
          icon: () => (<SchoolOutlined />),
          label: 'Lessons learned',
        }]
      : []),
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
