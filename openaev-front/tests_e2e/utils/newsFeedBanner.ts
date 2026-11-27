import { type Page } from '@playwright/test';

/**
 * The XTM Hub news feed register banner (#8938) shows up only after an asynchronous registration lookup and
 * then pushes the whole layout down: a click in flight lands on a moved target. Its dismissal is stored per
 * tenant, under the key of NewsFeedRegisterBannerProvider (useLocalStorage serialises the boolean as JSON).
 */
export const newsFeedBannerDismissedKey = (tenantId: string) => `news-feed-register-banner-dismissed-${tenantId}`;

/** Dismisses the banner of a tenant created by the test, as a user who closed it would have, from the page's next load. */
export const dismissNewsFeedBanner = async (page: Page, tenantId: string) => {
  await page.addInitScript(key => window.localStorage.setItem(key, 'true'), newsFeedBannerDismissedKey(tenantId));
};
