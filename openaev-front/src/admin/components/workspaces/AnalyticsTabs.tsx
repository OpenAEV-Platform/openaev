import { type FunctionComponent } from 'react';
import { useNavigate } from 'react-router';

import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import { useFormatter } from '../../../components/i18n';
import { DASHBOARD_BASE_URL } from '../../../constants/BaseUrls';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

export const REPORTING_BASE_URL = '/admin/reporting';

export type AnalyticsTab = 'dashboards' | 'reporting';

const TAB_PATHS: Record<AnalyticsTab, string> = {
  dashboards: DASHBOARD_BASE_URL,
  reporting: REPORTING_BASE_URL,
};

/**
 * The tabs of the one "Dashboards" entry of the left menu: custom dashboards and
 * reporting. Each tab keeps its own route (so every existing link, bookmark and
 * permission check is unchanged); the bar only shows the tabs the user can open.
 */
export const useAnalyticsTabs = () => {
  const ability = useAbility();
  const tabs: AnalyticsTab[] = [];
  if (ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS)) tabs.push('dashboards');
  if (ability.can(ACTIONS.ACCESS, SUBJECTS.REPORTINGS)) tabs.push('reporting');
  return tabs;
};

/** Where the merged menu entry lands: the first tab the user can open. */
export const analyticsEntryPath = (tabs: AnalyticsTab[]): string => TAB_PATHS[tabs[0] ?? 'dashboards'];

const AnalyticsTabs: FunctionComponent<{ current: AnalyticsTab }> = ({ current }) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const tabs = useAnalyticsTabs();
  // A single tab is not a choice: the bar is drawn only when both can be opened.
  if (tabs.length < 2) return null;
  const labels: Record<AnalyticsTab, string> = {
    dashboards: t('Custom dashboards'),
    reporting: t('Reporting'),
  };
  const entries: TabsEntry[] = tabs.map(key => ({
    key,
    label: labels[key],
  }));
  return (
    <div style={{ marginBottom: 16 }}>
      <Tabs
        entries={entries}
        currentTab={current}
        onChange={key => navigate(TAB_PATHS[key as AnalyticsTab])}
      />
    </div>
  );
};

export default AnalyticsTabs;
