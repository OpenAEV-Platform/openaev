import { BugReportOutlined, RouteOutlined, StyleOutlined } from '@mui/icons-material';
import { LockPattern } from 'mdi-material-ui';
import { type FunctionComponent, memo, useContext } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const TaxonomiesMenuComponent: FunctionComponent = () => {
  const ability = useContext(AbilityContext);
  const canAccessTenantSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
  const canAccessTags
    = ability.can(ACTIONS.ACCESS, SUBJECTS.TAGS);

  const entries: RightMenuEntry[] = [
    ...(canAccessTags
      ? [{
          path: '/admin/settings/taxonomies/tags',
          icon: () => (<StyleOutlined />),
          label: 'Tags',
        }]
      : []),
    ...(canAccessTenantSettings
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
