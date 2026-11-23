import { Icon } from '@filigran/design-system';
import { type FunctionComponent, memo } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { canAccessTags, canAccessTenantSettings } from '../nav/config/settings.config';

const TaxonomiesMenuComponent: FunctionComponent = () => {
  const ability = useAbility();
  const hasTenantSettingsAccess = canAccessTenantSettings(ability);
  const hasTagsAccess = canAccessTags(ability);

  const entries: RightMenuEntry[] = [
    ...(hasTagsAccess
      ? [{
          path: '/admin/settings/taxonomies/tags',
          icon: () => (<Icon name="tag" size={16} />),
          label: 'Tags',
        }]
      : []),
    ...(hasTenantSettingsAccess
      ? [{
          path: '/admin/settings/taxonomies/attack_patterns',
          icon: () => (<Icon name="chess-knight" size={16} />),
          label: 'Attack patterns',
        },
        {
          path: '/admin/settings/taxonomies/kill_chain_phases',
          icon: () => (<Icon name="route" size={16} />),
          label: 'Kill chain phases',
        },
        {
          path: '/admin/settings/taxonomies/vulnerabilities',
          icon: () => (<Icon name="bug" size={16} />),
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
