import { DialogContentText } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import type { CustomDashboardUsageOutput } from '../../../../utils/api-types';

interface Props { usage: CustomDashboardUsageOutput }

/** The usage lines of a custom dashboard, in the order they read in the delete dialog. */
export const customDashboardUsageLines = (
  usage: CustomDashboardUsageOutput,
  t: (message: string, values?: Record<string, string | number>) => string,
): string[] => {
  const lines: string[] = [];
  if (usage.scenarios_count > 0) {
    lines.push(usage.scenarios_count === 1
      ? t('Used by 1 scenario')
      : t('Used by {count} scenarios', { count: usage.scenarios_count }));
  }
  if (usage.simulations_count > 0) {
    lines.push(usage.simulations_count === 1
      ? t('Used by 1 simulation')
      : t('Used by {count} simulations', { count: usage.simulations_count }));
  }
  if (usage.default_scenario_dashboard) {
    lines.push(t('Default scenario dashboard'));
  }
  if (usage.default_simulation_dashboard) {
    lines.push(t('Default simulation dashboard'));
  }
  return lines;
};

/**
 * Tells what deleting the dashboard changes: the scenarios and simulations that show it fall back to
 * the default dashboard, and a default that names it is emptied. The default home dashboard is not
 * listed: its delete is refused and not offered.
 */
const CustomDashboardDeleteUsage: FunctionComponent<Props> = ({ usage }) => {
  const { t } = useFormatter();
  const lines = customDashboardUsageLines(usage, t);
  if (lines.length === 0) {
    return null;
  }
  return (
    <DialogContentText component="div" sx={{ marginTop: 1 }}>
      {t('This dashboard is in use. Deleting it removes it from:')}
      <ul style={{
        margin: 0,
        marginTop: 4,
        paddingLeft: 20,
      }}
      >
        {lines.map(line => <li key={line}>{line}</li>)}
      </ul>
    </DialogContentText>
  );
};

export default CustomDashboardDeleteUsage;
