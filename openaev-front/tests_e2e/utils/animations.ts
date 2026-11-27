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
    const addStyle = () => {
      const style = document.createElement('style');
      style.textContent = '*, *::before, *::after { transition: none !important; animation: none !important; }';
      document.head.appendChild(style);
    };
    if (document.head) {
      addStyle();
    } else {
      document.addEventListener('DOMContentLoaded', addStyle, { once: true });
    }
  });
};
