import { createContext, useContext } from 'react';

import { type XtmHubRegistrationOutput } from '../../../utils/api-types';

/** Per-tenant: registering one tenant says nothing about the others, and dismissing here must not hide it there. */
export const REGISTER_BANNER_DISMISSED_KEY_PREFIX = 'news-feed-register-banner-dismissed';

/** A tenant that lost its connectivity is still registered: it gets its news feed, so there is nothing to connect. */
export const isRegistered = (registration: Pick<XtmHubRegistrationOutput, 'tenant_xtmhub_registration_status'> | null | undefined) => {
  const status = registration?.tenant_xtmhub_registration_status;
  return status === 'REGISTERED' || status === 'LOST_CONNECTIVITY';
};

export interface NewsFeedRegisterBannerState {
  /** Whether the banner inviting to connect the product to XTM Hub is displayed. */
  isShown: boolean;
  dismiss: () => void;
}

/** Without a provider (public pages, tests) the banner is simply not shown. */
export const NewsFeedRegisterBannerContext = createContext<NewsFeedRegisterBannerState>({
  isShown: false,
  dismiss: () => {},
});

export const useNewsFeedRegisterBanner = () => useContext(NewsFeedRegisterBannerContext);
