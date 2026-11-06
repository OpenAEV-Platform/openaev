import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type LoggedHelper } from '../../../actions/helper';
import useBannerSettings from '../../../public/components/systembanners/useBannerSettings';
import { TOP_BANNER_HEIGHT } from '../../../public/components/trialbanners/constants';
import NewsFeedRegisterBanner from '../../../public/components/trialbanners/NewsFeedRegisterBanner';
import NewsFeedRegisterBannerProvider from '../../../public/components/trialbanners/NewsFeedRegisterBannerProvider';
import { useHelper } from '../../../store';
import { type PlatformSettings, type User, type XtmHubRegistrationOutput } from '../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../utils/hooks/useAuth';
import { type AppAbility, defineAbility } from '../../../utils/permissions/ability';
import { AbilityProvider } from '../../../utils/permissions/permissionsContext';

const { mockDispatch } = vi.hoisted(() => ({ mockDispatch: vi.fn() }));

vi.mock('../../../store', () => ({ useHelper: vi.fn() }));
vi.mock('../../../utils/hooks', () => ({ useAppDispatch: () => mockDispatch }));
vi.mock('../../../actions/xtmhub/xtmhub-actions', () => ({ fetchXtmHubRegistration: vi.fn() }));

const ADMIN_ABILITY: AppAbility = defineAbility([], {}, true);
const NON_ADMIN_ABILITY: AppAbility = defineAbility(['ACCESS_TENANT_SETTINGS'], {}, false);
const DISMISSED_KEY = 'news-feed-register-banner-dismissed-tenant-1';

interface Scenario {
  ability?: AppAbility;
  flagEnabled?: boolean;
  reachable?: boolean;
  registration?: XtmHubRegistrationOutput | null;
}

let scenario: Scenario = {};

const Probe = () => {
  const { bannerHeightNumber } = useBannerSettings({} as PlatformSettings);
  return <span data-testid="banner-height">{bannerHeightNumber}</span>;
};

const LocationDisplay = () => <span data-testid="location">{useLocation().pathname}</span>;

const Tree = ({ children }: { children?: ReactNode }) => {
  const { ability = ADMIN_ABILITY, flagEnabled = true, reachable = true, registration = null } = scenario;
  const settings = { enabled_dev_features: flagEnabled ? ['XTM_HUB_NEWS_FEED'] : [] } as PlatformSettings;
  const helper = {
    getPlatformSettings: () => settings,
    getXtmHubRegistration: () => registration,
  } as unknown as LoggedHelper;
  vi.mocked(useHelper).mockImplementation((selector: (helper: LoggedHelper) => unknown) => selector(helper));
  const userContext = {
    me: { user_id: 'user-1' } as User,
    settings,
    isXTMHubAccessible: reachable,
    currentUserTenant: { tenant_id: 'tenant-1' },
  } as UserContextType;
  return (
    <MemoryRouter>
      <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
        <UserContext.Provider value={userContext}>
          <AbilityProvider value={ability}>
            <NewsFeedRegisterBannerProvider>
              <NewsFeedRegisterBanner />
              <Probe />
              <LocationDisplay />
              {children}
            </NewsFeedRegisterBannerProvider>
          </AbilityProvider>
        </UserContext.Provider>
      </IntlProvider>
    </MemoryRouter>
  );
};

const renderBanner = (overrides: Scenario = {}) => {
  scenario = overrides;
  return render(<Tree />);
};

const rerenderBanner = (view: ReturnType<typeof render>, overrides: Scenario) => {
  scenario = overrides;
  view.rerender(<Tree />);
};

const BANNER_TEXT = 'New feeds are available.';
const registrationWith = (status: XtmHubRegistrationOutput['tenant_xtmhub_registration_status']) => ({ tenant_xtmhub_registration_status: status });

describe('NewsFeedRegisterBanner', () => {
  beforeEach(() => {
    localStorage.clear();
    mockDispatch.mockImplementation(() => Promise.resolve());
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    scenario = {};
  });

  describe('for an admin on an unregistered tenant', () => {
    it('given the feature flag is on should invite to connect the product', async () => {
      renderBanner();

      expect(await screen.findByText(BANNER_TEXT)).toBeInTheDocument();
      expect(screen.getByText('Connect your product to access and deploy these feeds in one click.', { exact: false })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Connect Product' })).toBeInTheDocument();
    });

    it('given a click on the button should open the XTM Hub connect flow', async () => {
      renderBanner();

      fireEvent.click(await screen.findByRole('button', { name: 'Connect Product' }));

      expect(screen.getByTestId('location')).toHaveTextContent('/redirect/connect-xtm-hub');
    });

    it('given an unregistered status should still show it', async () => {
      renderBanner({ registration: registrationWith('UNREGISTERED') });

      expect(await screen.findByText(BANNER_TEXT)).toBeInTheDocument();
    });

    it('given the banner is shown should reserve the top banner height', async () => {
      renderBanner();

      await waitFor(() => expect(screen.getByTestId('banner-height')).toHaveTextContent(String(TOP_BANNER_HEIGHT)));
    });

    it('given the answer is not back yet should not show it', async () => {
      mockDispatch.mockImplementation(() => new Promise(() => {}));
      renderBanner();

      await Promise.resolve();

      expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument();
      expect(screen.getByTestId('banner-height')).toHaveTextContent('0');
    });
  });

  describe('once the tenant is registered', () => {
    it.each(['REGISTERED', 'LOST_CONNECTIVITY'] as const)('given a %s tenant should not show it', async (status) => {
      renderBanner({ registration: registrationWith(status) });

      await waitFor(() => expect(mockDispatch).toHaveBeenCalled());
      expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument();
      expect(screen.getByTestId('banner-height')).toHaveTextContent('0');
    });

    it('given the tenant registers while it is shown should make it disappear', async () => {
      const view = renderBanner();
      await screen.findByText(BANNER_TEXT);

      rerenderBanner(view, { registration: registrationWith('REGISTERED') });

      await waitFor(() => expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument());
    });
  });

  describe('for a user who cannot connect the product', () => {
    it('given a non-admin user should not show it nor ask for the registration', async () => {
      renderBanner({ ability: NON_ADMIN_ABILITY });

      await Promise.resolve();

      expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument();
      expect(mockDispatch).not.toHaveBeenCalled();
    });
  });

  describe('when XTM_HUB_NEWS_FEED is off', () => {
    it('given the feature flag is off should not show it nor ask for the registration', async () => {
      renderBanner({ flagEnabled: false });

      await Promise.resolve();

      expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument();
      expect(mockDispatch).not.toHaveBeenCalled();
    });
  });

  describe('when XTM Hub cannot be reached', () => {
    it('given an unreachable hub should not show it', async () => {
      renderBanner({ reachable: false });

      await waitFor(() => expect(mockDispatch).toHaveBeenCalled());
      expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument();
    });
  });

  describe('dismissal', () => {
    it('given a click on close should hide it and reserve no height', async () => {
      renderBanner();
      await screen.findByText(BANNER_TEXT);

      fireEvent.click(screen.getByRole('button', { name: 'Close' }));

      expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument();
      expect(screen.getByTestId('banner-height')).toHaveTextContent('0');
    });

    it('given the page is reloaded after a dismissal should keep it hidden', async () => {
      const first = renderBanner();
      await screen.findByText(BANNER_TEXT);
      fireEvent.click(screen.getByRole('button', { name: 'Close' }));
      first.unmount();

      renderBanner();

      await waitFor(() => expect(mockDispatch).toHaveBeenCalledTimes(2));
      expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument();
    });

    it('given a dismissal on another tenant should still show it', async () => {
      localStorage.setItem('news-feed-register-banner-dismissed-tenant-2', 'true');

      renderBanner();

      expect(await screen.findByText(BANNER_TEXT)).toBeInTheDocument();
    });

    it('given the tenant gets registered should forget the dismissal so it returns if unregistered later', async () => {
      localStorage.setItem(DISMISSED_KEY, 'true');
      const view = renderBanner({ registration: registrationWith('REGISTERED') });
      await waitFor(() => expect(localStorage.getItem(DISMISSED_KEY)).toBe('false'));

      rerenderBanner(view, { registration: null });

      expect(await screen.findByText(BANNER_TEXT)).toBeInTheDocument();
    });
  });
});
