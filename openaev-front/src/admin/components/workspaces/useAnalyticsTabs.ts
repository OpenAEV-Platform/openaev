import { DASHBOARD_BASE_URL } from '../../../constants/BaseUrls';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

export const REPORTING_BASE_URL = '/admin/reporting';

export type AnalyticsTab = 'dashboards' | 'reporting';

export const ANALYTICS_TAB_PATHS: Record<AnalyticsTab, string> = {
  dashboards: DASHBOARD_BASE_URL,
  reporting: REPORTING_BASE_URL,
};

/**
 * The tabs of the one "Dashboards" entry of the left menu: custom dashboards and
 * reporting. Each tab keeps its own route (so every existing link, bookmark and
 * permission check is unchanged); only the tabs the user can open are listed.
 */
const useAnalyticsTabs = (): AnalyticsTab[] => {
  const ability = useAbility();
  const tabs: AnalyticsTab[] = [];
  if (ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS)) tabs.push('dashboards');
  if (ability.can(ACTIONS.ACCESS, SUBJECTS.REPORTINGS)) tabs.push('reporting');
  return tabs;
};

/** Where the merged menu entry lands: the first tab the user can open. */
export const analyticsEntryPath = (tabs: AnalyticsTab[]): string => ANALYTICS_TAB_PATHS[tabs[0] ?? 'dashboards'];

export default useAnalyticsTabs;
