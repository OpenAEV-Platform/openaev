import {
  DashboardOutlined,
  DescriptionOutlined,
  DnsOutlined,
  DomainOutlined,
  ExtensionOutlined,
  GroupsOutlined,
  HubOutlined,
  InsertChartOutlined,
  KeyOutlined,
  LayersOutlined,
  PersonOutlined,
  PhishingOutlined,
  PlayCircleOutlineOutlined,
  RocketLaunchOutlined,
  RouteOutlined,
  RowingOutlined,
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

import AppNavbar from '../../../components/common/menu/navbar/AppNavbar';
import { type NavMenuEntries } from '../../../components/common/menu/navbar/nav-menu-model';
import { DASHBOARD_BASE_URL } from '../../../constants/BaseUrls';
import useAuth from '../../../utils/hooks/useAuth';
import useExplorationGraph from '../../../utils/hooks/useExplorationGraph';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../utils/utils';
import { GETTING_STARTED_URI } from '../getting_started/GettingStartedRoutes';
import useAnalyticsTabs, { analyticsEntryPath, REPORTING_BASE_URL } from '../workspaces/useAnalyticsTabs';
import { ASSESSMENT_ACCESS_CHECKS } from './config/assessment.config';
import settingsEntries from './config/settings.config';
import LeftBarHeader from './LeftBarHeader';
import TenantSwitcher from './LeftBarTenantSwitcher';

const LeftBar = () => {
  const ability = useAbility();
  const { userTenants } = useAuth();
  // The tenant switcher only appears when the user can switch (more than one
  // tenant). Passing no headerElement in the single-tenant case keeps the menu
  // clean and avoids an orphan divider above the first entry (Home).
  const hasTenantSwitcher = (userTenants ?? []).length > 1;
  const isCredentialAssetEnabled = isFeatureEnabled('CREDENTIAL_ASSET');
  const analyticsTabs = useAnalyticsTabs();
  // The exploration graph preview feature (off by default) brings the Graph entry and folds
  // Dashboards and Reporting into one entry; off, the menu is the one shipped before it.
  const graphEnabled = useExplorationGraph();
  // Same checks as the list routes: users with grants only still reach what is granted to them
  const canAccessAssessments = ASSESSMENT_ACCESS_CHECKS.some(({ action, subject }) => ability.can(action, subject));

  const entries: NavMenuEntries[] = [
    {
      userRight: true,
      items: [
        {
          path: `/admin`,
          icon: () => (<DashboardOutlined />),
          label: 'Home',
          userRight: true,
        },
        ...(graphEnabled
          ? [
              {
                path: '/admin/graph',
                icon: () => (<HubOutlined />),
                label: 'Graph',
                userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
              },
              {
                // One entry, two tabs (Custom dashboards | Reporting): it lands on the
                // first tab the user can open and stays current on both routes.
                path: analyticsEntryPath(analyticsTabs),
                matchPaths: [DASHBOARD_BASE_URL, REPORTING_BASE_URL],
                icon: () => (<InsertChartOutlined />),
                label: 'Dashboards',
                userRight: analyticsTabs.length > 0,
              },
            ]
          : [
              {
                path: DASHBOARD_BASE_URL,
                icon: () => (<InsertChartOutlined />),
                label: 'Dashboards',
                userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS),
              },
              {
                path: REPORTING_BASE_URL,
                icon: () => (<FileChartOutline />),
                label: 'Reporting',
                userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.REPORTINGS),
              },
            ]),
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
          userRight: canAccessAssessments,
        },
        {
          path: `/admin/simulations`,
          icon: () => (<PlayCircleOutlineOutlined />),
          label: 'Simulations',
          userRight: canAccessAssessments,
        },
        {
          path: `/admin/atomic_testings`,
          icon: () => (<Target />),
          label: 'Atomic testings',
          userRight: canAccessAssessments,
        },
        {
          path: `/admin/threat-arsenal`,
          icon: () => (<LayersOutlined />),
          label: 'Threat Arsenal',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.THREAT_ARSENALS)
            || ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS)
            || ability.can(ACTIONS.ACCESS, SUBJECTS.THREAT_ARSENAL_RESOURCE),
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
        ...isCredentialAssetEnabled
          ? [{
              path: `/admin/credentials`,
              icon: () => (<KeyOutlined />),
              label: 'Credentials',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.CREDENTIALS),
            }]
          : [],
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
            || ability.can(ACTIONS.ACCESS, SUBJECTS.CHALLENGES),
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
              link: '/admin/components/phishing',
              label: 'Phishing',
              icon: () => (<PhishingOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PHISHING),
            },
            {
              link: '/admin/components/challenges',
              label: 'Challenges',
              icon: () => (<RowingOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.CHALLENGES),
            },
          ],
        },
      ],
    },
  ];
  const settingsItems = settingsEntries(ability);
  entries.push(
    // Always visible: "Settings" hides itself when the user lacks the right.
    {
      userRight: true,
      items: [
        ...settingsItems,
        {
          path: `/admin/${GETTING_STARTED_URI}`,
          icon: () => (<RocketLaunchOutlined />),
          label: 'Getting Started',
          userRight: true,
        },
      ],
    },
  );
  return (
    <AppNavbar
      entries={entries}
      header={() => <LeftBarHeader />}
      headerElement={hasTenantSwitcher ? (navOpen: boolean) => <TenantSwitcher navOpen={navOpen} /> : undefined}
    />
  );
};

export default LeftBar;
