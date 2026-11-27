import { type Page } from '@playwright/test';

/**
 * Turns off CSS transitions and animations on every document the page loads, so an action never targets an
 * element still moving into place: a click on a MUI menu item while the menu grows open lands where the item
 * was a frame earlier, on another item or on nothing.
 *
 * An init script runs before the document has a `<head>`, so the style is added once the document is parsed.
 */
export const disableAnimations = async (page: Page) => {
  await page.addInitScript(() => {
    const style = document.createElement('style');
    style.textContent = `
      *, *::before, *::after {
        transition: none !important;
        animation: none !important;
      }
    `;
    // Init scripts run as soon as the document is created, before the parser adds <head>.
    // Wait for it: the observer fires before the parser runs the first script, so the
    // style is still in place before the app's code.
    if (document.head) {
      document.head.appendChild(style);
      return;
    }
    const observer = new MutationObserver(() => {
      if (document.head) {
        observer.disconnect();
        document.head.appendChild(style);
      }
    });
    observer.observe(document, {
      childList: true,
      subtree: true,
    });
  });
};
