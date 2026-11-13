import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PhishingPage from '../../../../public/components/phishing/PhishingPage';

vi.mock('../../../../network', async importOriginal => ({
  ...(await importOriginal<object>()),
  api: () => ({ get: () => Promise.resolve({ data: { phishing_landing_page_html: '<a href="#reset">Reset password</a>' } }) }),
}));

const RENDERED_AT = new Date('2026-11-10T10:00:00Z').getTime();

const renderPage = async (route: string) => {
  render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="auth/:token" element={<PhishingPage />} />
        <Route path="phishing/:tenantId/:token" element={<PhishingPage />} />
      </Routes>
    </MemoryRouter>,
  );
  return screen.findByText('Reset password');
};

const waitMs = (ms: number) => vi.setSystemTime(RENDERED_AT + ms);

describe('PhishingPage', () => {
  const sendBeacon = vi.fn(() => true);

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(RENDERED_AT);
    Object.defineProperty(navigator, 'sendBeacon', {
      value: sendBeacon,
      configurable: true,
    });
    // jsdom reports itself as automated: start every test as a regular browser.
    Object.defineProperty(navigator, 'webdriver', {
      value: false,
      configurable: true,
    });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    sendBeacon.mockClear();
  });

  describe('click confirmation on the benign route', () => {
    it('given an interaction after the minimum dwell, should send one beacon with the dwell', async () => {
      // Arrange
      await renderPage('/auth/token-1');
      waitMs(1500);

      // Act
      fireEvent.pointerMove(window);
      fireEvent.keyDown(window);

      // Assert
      expect(sendBeacon).toHaveBeenCalledTimes(1);
      const [url, body] = sendBeacon.mock.calls[0] as unknown as [string, Blob];
      expect(url).toContain('/api/hosted/confirm/token-1');
      expect(body.type).toBe('application/json');
      expect(JSON.parse(await body.text())).toEqual({
        dwell_ms: 1500,
        webdriver: false,
      });
    });

    it('given an interaction before the minimum dwell, should ignore it and confirm the next one', async () => {
      // Arrange
      await renderPage('/auth/token-1');

      // Act
      waitMs(300);
      fireEvent.pointerMove(window);
      const beaconsBeforeDwell = sendBeacon.mock.calls.length;
      waitMs(900);
      fireEvent.scroll(window);

      // Assert
      expect(beaconsBeforeDwell).toBe(0);
      expect(sendBeacon).toHaveBeenCalledTimes(1);
    });

    it('given a browser driven by automation, should report it in the confirmation', async () => {
      // Arrange
      Object.defineProperty(navigator, 'webdriver', {
        value: true,
        configurable: true,
      });
      await renderPage('/auth/token-1');
      waitMs(1000);

      // Act
      fireEvent.pointerDown(window);

      // Assert
      const [, body] = sendBeacon.mock.calls[0] as unknown as [string, Blob];
      expect(JSON.parse(await body.text())).toMatchObject({ webdriver: true });
    });

    it('given a click with no pointer or keyboard event (assistive technology), should confirm', async () => {
      // Arrange
      const link = await renderPage('/auth/token-1');
      waitMs(1000);

      // Act
      fireEvent.click(link);

      // Assert
      expect(sendBeacon).toHaveBeenCalledTimes(1);
    });
  });

  describe('legacy route', () => {
    it('given an interaction, should not send any confirmation', async () => {
      // Arrange
      await renderPage('/phishing/tenant-1/token-1');
      waitMs(1500);

      // Act
      fireEvent.pointerDown(window);

      // Assert
      expect(sendBeacon).not.toHaveBeenCalled();
    });
  });
});
