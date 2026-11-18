import { type FunctionComponent } from 'react';
import { useNavigate } from 'react-router';

import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import { useFormatter } from '../../../components/i18n';
import useAnalyticsTabs, { ANALYTICS_TAB_PATHS, type AnalyticsTab } from './useAnalyticsTabs';

/**
 * Custom dashboards | Reporting: the tab bar of the one "Dashboards" entry of the
 * left menu, drawn on both list pages. A single tab is not a choice, so the bar
 * is drawn only when the user can open both.
 */
const AnalyticsTabs: FunctionComponent<{ current: AnalyticsTab }> = ({ current }) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const tabs = useAnalyticsTabs();
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
        onChange={key => navigate(ANALYTICS_TAB_PATHS[key as AnalyticsTab])}
      />
    </div>
  );
};

export default AnalyticsTabs;
