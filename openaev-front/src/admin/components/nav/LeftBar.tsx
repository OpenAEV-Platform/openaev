import { DashboardOutlined, DescriptionOutlined, DevicesOtherOutlined, DnsOutlined, Groups3Outlined, GroupsOutlined, HubOutlined, InsertChartOutlined, MovieFilterOutlined, OnlinePredictionOutlined, PersonOutlined, RocketLaunchOutlined, RowingOutlined, SchoolOutlined, SettingsOutlined, SmartButtonOutlined, SubscriptionsOutlined, TerminalOutlined } from '@mui/icons-material';
import { Binoculars, NewspaperVariantMultipleOutline, PostOutline, SecurityNetwork, SelectGroup, Target } from 'mdi-material-ui';
import { useContext } from 'react';

import LeftMenu from '../../../components/common/menu/leftmenu/LeftMenu';
import { AbilityContext } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { GETTING_STARTED_URI } from '../getting_started/GettingStartedRoutes';

const LeftBar = () => {
  const ability = useContext(AbilityContext);
  const entries = [
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
          icon: () => (<MovieFilterOutlined />),
          label: 'Scenarios',
          userRight: true,
        },
        {
          path: `/admin/simulations`,
          icon: () => (<HubOutlined />),
          label: 'Simulations',
          userRight: true,
        },
        {
          path: `/admin/atomic_testings`,
          icon: () => (<Target />),
          label: 'Atomic testings',
          userRight: true,
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
          href: 'assets',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS) || ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS),
          subItems: [
            {
              link: '/admin/assets/endpoints',
              label: 'Endpoints',
              icon: () => (<DevicesOtherOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
            },
            {
              link: '/admin/assets/asset_groups',
              label: 'Asset groups',
              icon: () => (<SelectGroup fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
            },
            {
              link: '/admin/assets/security_platforms',
              label: 'Security platforms',
              icon: () => (<SecurityNetwork fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS),
            },
          ],
        },
        {
          path: `/admin/teams`,
          icon: () => (<Groups3Outlined />),
          label: 'People',
          href: 'teams',
          userRight: true,
          subItems: [
            {
              link: '/admin/teams/players',
              label: 'Players',
              icon: () => (<PersonOutlined fontSize="small" />),
              userRight: true,
            },
            {
              link: '/admin/teams/teams',
              label: 'Teams',
              icon: () => (<GroupsOutlined fontSize="small" />),
              userRight: true,
            },
          ],
        },
        {
          path: `/admin/components`,
          icon: () => (<NewspaperVariantMultipleOutline />),
          label: 'Components',
          href: 'components',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)
            || ability.can(ACTIONS.ACCESS, SUBJECTS.CHANNELS)
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
    {
      userRight: true,
      items: [
        {
          path: `/admin/payloads`,
          icon: () => (<SubscriptionsOutlined />),
          label: 'Payloads',
          userRight: true,
        },
        {
          path: `/admin/integrations`,
          icon: () => (<DnsOutlined />),
          label: 'Integrations',
          href: 'integrations',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
          subItems: [
            {
              link: '/admin/integrations/injectors',
              label: 'Injectors',
              icon: () => (<SmartButtonOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/integrations/collectors',
              label: 'Collectors',
              icon: () => (<OnlinePredictionOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/integrations/executors',
              label: 'Executors',
              icon: () => (<TerminalOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
          ],
        },
      ],
    },
    {
      userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
      items: [
        {
          path: `/admin/settings`,
          icon: () => (<SettingsOutlined />),
          label: 'Settings',
          href: 'settings',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
          subItems: [
            {
              link: '/admin/settings/parameters',
              label: 'Parameters',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/settings/security',
              label: 'Security',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/settings/asset_rules',
              label: 'Customization',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/settings/taxonomies',
              label: 'Taxonomies',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/settings/data_ingestion',
              label: 'Data ingestion',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/settings/experience',
              label: 'Filigran Experience',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
          ],
        },
      ],
    },
  ];
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
    <LeftMenu entries={entries} bottomEntries={bottomEntries} />
  );
};

export default LeftBar;
