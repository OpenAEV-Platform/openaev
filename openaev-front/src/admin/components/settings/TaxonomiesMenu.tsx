import { BugReportOutlined, RouteOutlined, StyleOutlined } from '@mui/icons-material';
import { LockPattern } from 'mdi-material-ui';
import { type FunctionComponent, memo, useContext } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { canAccessTags, canAccessTenantSettings } from '../nav/config/settings.config';

const TaxonomiesMenuComponent: FunctionComponent = () => {
  const ability = useContext(AbilityContext);
  const hasTenantSettingsAccess = canAccessTenantSettings(ability);
  const hasTagsAccess = canAccessTags(ability);

  const entries: RightMenuEntry[] = [
    ...(hasTagsAccess
      ? [{
          path: '/admin/settings/taxonomies/tags',
          icon: () => (<StyleOutlined />),
          label: 'Tags',
        }]
      : []),
    ...(hasTenantSettingsAccess
      ? [{
          path: '/admin/settings/taxonomies/attack_patterns',
          icon: () => (<LockPattern />),
          label: 'Attack patterns',
        },
        {
          path: '/admin/settings/taxonomies/kill_chain_phases',
          icon: () => (<RouteOutlined />),
          label: 'Kill chain phases',
        },
        {
          path: '/admin/settings/taxonomies/vulnerabilities',
          icon: () => (<BugReportOutlined />),
          label: 'Vulnerabilities',
        }]
      : []),
  ];

  return (
    <RightMenu entries={entries} />
  );
};

const TaxonomiesMenu = memo(TaxonomiesMenuComponent);

export default TaxonomiesMenu;
