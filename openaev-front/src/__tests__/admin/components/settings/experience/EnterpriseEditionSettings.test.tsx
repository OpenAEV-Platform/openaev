import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import EnterpriseEditionSettings from '../../../../../admin/components/settings/experience/EnterpriseEditionSettings';
import ThemeDark from '../../../../../components/ThemeDark';
import { useHelper } from '../../../../../store';
import { type License, type PlatformSettings, type User } from '../../../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../../../utils/hooks/useAuth';
import { type AppAbility, defineAbility } from '../../../../../utils/permissions/ability';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';

vi.mock('../../../../../store', () => ({ useHelper: vi.fn() }));
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../actions/Application', () => ({
  updateChatbotAiCguStatus: vi.fn(),
  updatePlatformEnterpriseEditionParameters: vi.fn(),
}));

const theme = createTheme(ThemeDark());
const ability: AppAbility = defineAbility([], {}, true);

const renderSettings = (license: License) => {
  const settings = {
    platform_license: license,
    filigran_chatbot_ai_cgu_status: 'enabled',
  } as PlatformSettings;
  vi.mocked(useHelper).mockReturnValue({ settings });
  const userContext = {
    me: { user_id: 'user-1' } as User,
    settings,
  } as UserContextType;
  const wrapper = ({ children }: { children: ReactNode }) => (
    <ThemeProvider theme={theme}>
      <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
        <UserContext.Provider value={userContext}>
          <AbilityContext.Provider value={ability}>
            {children}
          </AbilityContext.Provider>
        </UserContext.Provider>
      </IntlProvider>
    </ThemeProvider>
  );
  return render(<EnterpriseEditionSettings />, { wrapper });
};

const ACTIVE_LICENSE: License = {
  license_is_enterprise: true,
  license_is_validated: true,
  license_is_expired: false,
  license_customer: 'ACME',
  license_type: 'standard',
};

describe('EnterpriseEditionSettings', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('shows that Enterprise Edition comes from the XTM One license, without offering to disable it', () => {
    renderSettings({
      ...ACTIVE_LICENSE,
      license_source: 'xtm_one',
    });

    expect(screen.getByText('XTM One license')).toBeDefined();
    expect(screen.getByText('ACME')).toBeDefined();
    expect(screen.getByText('Activated')).toBeDefined();
    expect(screen.getByText(/granted by the XTM license of the connected XTM One/)).toBeDefined();
    expect(screen.queryByRole('button', { name: 'Disable Enterprise Edition' })).toBeNull();
    expect(screen.getByText('Manage your enterprise edition license')).toBeDefined();
  });

  it('shows the OpenAEV license as the source of its own license', () => {
    renderSettings({
      ...ACTIVE_LICENSE,
      license_source: 'openaev',
    });

    expect(screen.getByText('OpenAEV license')).toBeDefined();
    expect(screen.queryByText(/granted by the XTM license/)).toBeNull();
    expect(screen.getByRole('button', { name: 'Disable Enterprise Edition' })).toBeDefined();
  });

  it('shows Community Edition without a license', () => {
    renderSettings({ license_is_enterprise: false });

    expect(screen.getByText('Community edition')).toBeDefined();
    expect(screen.queryByText('XTM One license')).toBeNull();
  });
});
