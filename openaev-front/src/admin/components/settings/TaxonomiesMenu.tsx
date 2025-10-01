import { ReportProblemOutlined, RouteOutlined, StyleOutlined } from '@mui/icons-material';
import { LockPattern } from 'mdi-material-ui';
import { type FunctionComponent, memo } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';

const TaxonomiesMenuComponent: FunctionComponent = () => {
  const entries: RightMenuEntry[] = [
    {
      path: '/admin/settings/taxonomies/tags',
      icon: () => (<StyleOutlined />),
      label: 'Tags',
    },
    {
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
      path: '/admin/settings/taxonomies/cves',
      icon: () => (<ReportProblemOutlined />),
      label: 'Vulnerabilities',
    },
  ];

  return (
    <RightMenu entries={entries} />
  );
};

const TaxonomiesMenu = memo(TaxonomiesMenuComponent);

export default TaxonomiesMenu;
