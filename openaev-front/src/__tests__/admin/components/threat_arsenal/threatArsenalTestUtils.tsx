import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { render } from '@testing-library/react';
import { type ReactElement, type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';

import {
  type PayloadUsageOutput,
  type PayloadUsagesOutput,
  type PayloadVersionOutput,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';

export const intlWrapper = ({ children }: { children: ReactNode }) => (
  <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>{children}</IntlProvider>
);

export const renderInApp = (ui: ReactElement) => render(
  <MemoryRouter>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        {intlWrapper({ children: ui })}
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

export const aVersion = (overrides: Partial<PayloadVersionOutput> = {}): PayloadVersionOutput => ({
  payload_action_id: 'action-v1',
  payload_id: 'payload-v1',
  payload_is_latest: false,
  payload_older_versions_kept: false,
  payload_revoked: false,
  payload_revoked_by_catalogue: false,
  payload_version: '1',
  ...overrides,
});

export const aUsage = (overrides: Partial<PayloadUsageOutput> = {}): PayloadUsageOutput => ({
  usage_id: 'usage-1',
  usage_name: 'Dump credentials',
  usage_scenario_id: 'scenario-1',
  usage_scenario_name: 'Ransomware drill',
  usage_type: 'inject',
  ...overrides,
});

export const someUsages = (usages: PayloadUsageOutput[], hidden = 0): PayloadUsagesOutput => ({
  payload_usages: usages,
  payload_usages_hidden_count: hidden,
});

export const anAction = (overrides: Partial<ThreatArsenalAction> = {}): ThreatArsenalAction => ({
  action_attack_patterns_ids: [],
  action_domains_ids: [],
  action_grant_resource_id: 'grant-1',
  action_labels: { en: 'Mimikatz' },
  action_payload: {
    payload_id: 'payload-v2',
    payload_status: 'VERIFIED',
  },
  injector_contract_id: 'action-v2',
  injector_contract_updated_at: '2026-01-01T00:00:00Z',
  ...overrides,
});
