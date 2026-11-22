import { Icon } from '@filigran/design-system';

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
          icon: () => (<Icon name="house" size={16} />),
          label: 'Home',
          userRight: true,
        },
        ...(graphEnabled
          ? [
              {
                path: '/admin/graph',
                icon: () => (<Icon name="waypoints" size={16} />),
                label: 'Graph',
                userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
              },
              {
                // One entry, two tabs (Custom dashboards | Reporting): it lands on the
                // first tab the user can open and stays current on both routes.
                path: analyticsEntryPath(analyticsTabs),
                matchPaths: [DASHBOARD_BASE_URL, REPORTING_BASE_URL],
                icon: () => (<Icon name="layout-dashboard" size={16} />),
                label: 'Dashboards',
                userRight: analyticsTabs.length > 0,
              },
            ]
          : [
              {
                path: DASHBOARD_BASE_URL,
                icon: () => (<Icon name="layout-dashboard" size={16} />),
                label: 'Dashboards',
                userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS),
              },
              {
                path: REPORTING_BASE_URL,
                icon: () => (<Icon name="file-text" size={16} />),
                label: 'Reporting',
                userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.REPORTINGS),
              },
            ]),
        {
          path: '/admin/findings',
          icon: () => (<Icon name="custom/binocular" size={16} />),
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
          icon: () => (<Icon name="route" size={16} />),
          label: 'Scenarios',
          userRight: canAccessAssessments,
        },
        {
          path: `/admin/simulations`,
          icon: () => (<Icon name="circle-play" size={16} />),
          label: 'Simulations',
          userRight: canAccessAssessments,
        },
        {
          path: `/admin/atomic_testings`,
          icon: () => (<Icon name="custom/target" size={16} />),
          label: 'Atomic testings',
          userRight: canAccessAssessments,
        },
        {
          path: `/admin/threat-arsenal`,
          icon: () => (<Icon name="layers" size={16} />),
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
          icon: () => (<Icon name="server" size={16} />),
          label: 'Assets',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
        },
        {
          path: `/admin/asset_groups`,
          icon: () => (<Icon name="custom/asset-groups" size={16} />),
          label: 'Asset groups',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
        },
        ...isCredentialAssetEnabled
          ? [{
              path: `/admin/credentials`,
              icon: () => (<Icon name="key-round" size={16} />),
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
          icon: () => (<Icon name="user" size={16} />),
          label: 'Persons',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TEAMS_AND_PLAYERS),
        },
        {
          path: `/admin/teams`,
          icon: () => (<Icon name="users" size={16} />),
          label: 'Teams',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TEAMS_AND_PLAYERS),
        },
        {
          path: `/admin/organizations`,
          icon: () => (<Icon name="building-2" size={16} />),
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
          icon: () => (<Icon name="puzzle" size={16} />),
          label: 'Integrations',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
        },
        {
          path: `/admin/security_platforms`,
          icon: () => (<Icon name="custom/security-platforms" size={16} />),
          label: 'Security platforms',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS),
        },
        {
          path: `/admin/components`,
          icon: () => (<Icon name="newspaper" size={16} />),
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
              icon: () => (<Icon name="file" size={16} />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS),
            },
            {
              link: '/admin/components/channels',
              label: 'Channels',
              icon: () => (<Icon name="megaphone" size={16} />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.CHANNELS),
            },
            {
              link: '/admin/components/phishing',
              label: 'Phishing',
              icon: () => (<Icon name="mail" size={16} />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PHISHING),
            },
            {
              link: '/admin/components/challenges',
              label: 'Challenges',
              icon: () => (<Icon name="flag" size={16} />),
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
          icon: () => (<Icon name="rocket" size={16} />),
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
