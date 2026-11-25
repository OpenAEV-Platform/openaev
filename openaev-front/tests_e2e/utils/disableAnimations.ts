import { type Page } from '@playwright/test';

/**
 * Turns off every CSS transition and animation on each page load, before the app's scripts run.
 * A drawer still sliding in or a backdrop still fading out can intercept a click, or let a visibility
 * check pass on a panel that is not rendered yet; without motion, each UI change is immediate.
 * Call it before the first navigation.
 */
const disableAnimations = async (page: Page) => {
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

export default disableAnimations;
