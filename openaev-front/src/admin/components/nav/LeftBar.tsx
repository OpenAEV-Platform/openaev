import {
  DashboardOutlined,
  DescriptionOutlined,
  DnsOutlined,
  DomainOutlined,
  ExtensionOutlined,
  GroupsOutlined,
  InsertChartOutlined,
  KeyOutlined,
  LayersOutlined,
  MailOutlineOutlined,
  PersonOutlined,
  PlayCircleOutlineOutlined,
  PublicOutlined,
  RocketLaunchOutlined,
  RouteOutlined,
  RowingOutlined,
  SchoolOutlined,
} from '@mui/icons-material';
import {
  Binoculars,
  FileChartOutline,
  NewspaperVariantMultipleOutline,
  PostOutline,
  SecurityNetwork,
  SelectGroup,
  Target,
} from 'mdi-material-ui';
import { useContext } from 'react';

import LeftMenu from '../../../components/common/menu/leftmenu/LeftMenu';
import { type LeftMenuEntries } from '../../../components/common/menu/leftmenu/leftmenu-model';
import useAuth from '../../../utils/hooks/useAuth';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { GETTING_STARTED_URI } from '../getting_started/GettingStartedRoutes';
import settingsEntries from './config/settings.config';
import LeftBarHeader from './LeftBarHeader';
import TenantSwitcher from './LeftBarTenantSwitcher';

const LeftBar = () => {
  const ability = useContext(AbilityContext);
  const { userTenants } = useAuth();
  // The tenant switcher only appears when the user can switch (more than one
  // tenant). Passing no headerElement in the single-tenant case keeps the menu
  // clean and avoids an orphan divider above the first entry (Home).
  const hasTenantSwitcher = (userTenants ?? []).length > 1;
  const entries: LeftMenuEntries[] = [
    {
      userRight: true,
      items: [
        {
          path: `/admin`,
          icon: () => (<DashboardOutlined />),
          label: 'Home',
          userRight: true,
        },
        {
          path: `/admin/workspaces/custom_dashboards`,
          icon: () => (<InsertChartOutlined />),
          label: 'Dashboards',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS),
        },
        {
          path: `/admin/reporting`,
          icon: () => (<FileChartOutline />),
          label: 'Reporting',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.REPORTINGS),
        },
        {
          path: '/admin/findings',
          icon: () => (<Binoculars />),
          label: 'Findings',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.FINDINGS),
        },
      ],
    },
    {
      userRight: true,
      items: [
        {
          path: `/admin/scenarios`,
          icon: () => (<RouteOutlined />),
          label: 'Scenarios',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT),
        },
        {
          path: `/admin/simulations`,
          icon: () => (<PlayCircleOutlineOutlined />),
          label: 'Simulations',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT),
        },
        {
          path: `/admin/atomic_testings`,
          icon: () => (<Target />),
          label: 'Atomic testings',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT),
        },
        {
          path: `/admin/threat-arsenal`,
          icon: () => (<LayersOutlined />),
          label: 'Threat Arsenal',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.THREAT_ARSENALS) || ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS),
        },
      ],
    },
    {
      userRight: true,
      items: [
        {
          path: `/admin/assets`,
          icon: () => (<DnsOutlined />),
          label: 'Assets',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
        },
        {
          path: `/admin/asset_groups`,
          icon: () => (<SelectGroup />),
          label: 'Asset groups',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
        },
        {
          path: `/admin/credentials`,
          icon: () => (<KeyOutlined />),
          label: 'Credentials',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS),
        },
      ],
    },
    {
      userRight: true,
      items: [
        {
          path: `/admin/persons`,
          icon: () => (<PersonOutlined />),
          label: 'Persons',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TEAMS_AND_PLAYERS),
        },
        {
          path: `/admin/teams`,
          icon: () => (<GroupsOutlined />),
          label: 'Teams',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TEAMS_AND_PLAYERS),
        },
        {
          path: `/admin/organizations`,
          icon: () => (<DomainOutlined />),
          label: 'Organizations',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
        },
      ],
    },
    {
      userRight: true,
      items: [
        {
          path: `/admin/integrations`,
          icon: () => (<ExtensionOutlined />),
          label: 'Integrations',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
        },
        {
          path: `/admin/security_platforms`,
          icon: () => (<SecurityNetwork />),
          label: 'Security platforms',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS),
        },
        {
          path: `/admin/components`,
          icon: () => (<NewspaperVariantMultipleOutline />),
          label: 'Components',
          href: 'components',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)
            || ability.can(ACTIONS.ACCESS, SUBJECTS.CHANNELS)
            || ability.can(ACTIONS.ACCESS, SUBJECTS.PHISHING)
            || ability.can(ACTIONS.ACCESS, SUBJECTS.CHALLENGES)
            || ability.can(ACTIONS.ACCESS, SUBJECTS.LESSONS_LEARNED),
          subItems: [
            {
              link: '/admin/components/documents',
              label: 'Documents',
              icon: () => (<DescriptionOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS),
            },
            {
              link: '/admin/components/channels',
              label: 'Channels',
              icon: () => (<PostOutline fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.CHANNELS),
            },
            {
              link: '/admin/components/phishing/landing_pages',
              label: 'Phishing pages',
              icon: () => (<PublicOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PHISHING),
            },
            {
              link: '/admin/components/phishing/email_templates',
              label: 'Phishing emails',
              icon: () => (<MailOutlineOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PHISHING),
            },
            {
              link: '/admin/components/challenges',
              label: 'Challenges',
              icon: () => (<RowingOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.CHALLENGES),
            },
            {
              link: '/admin/components/lessons',
              label: 'Lessons learned',
              icon: () => (<SchoolOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.LESSONS_LEARNED),
            },
          ],
        },
      ],
    },
  ];
  const settingsItems = settingsEntries(ability);
  entries.push(
    {
      userRight: settingsItems.some(item => item.userRight),
      items: settingsItems,
    },
  );
  const bottomEntries = [
    {
      userRight: true,
      items: [
        {
          path: `/admin/${GETTING_STARTED_URI}`,
          icon: () => (<RocketLaunchOutlined />),
          label: 'Getting Started',
          userRight: true,
        },
      ],
    },
  ];
  return (
    <LeftMenu
      entries={entries}
      bottomEntries={bottomEntries}
      logoHeader={(navOpen: boolean) => <LeftBarHeader navOpen={navOpen} />}
      headerElement={hasTenantSwitcher ? (navOpen: boolean) => <TenantSwitcher navOpen={navOpen} /> : undefined}
    />
  );
};

export default LeftBar;
