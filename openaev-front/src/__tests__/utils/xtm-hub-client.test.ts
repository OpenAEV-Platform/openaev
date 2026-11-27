import { beforeEach, describe, expect, it, vi } from 'vitest';

import { sendErrorMessageToBackend } from '../../utils/Action';
import { getXtmHubOrigin, reportXtmHubOriginMismatch } from '../../utils/xtm-hub-client';

vi.mock('../../utils/Action', () => ({ sendErrorMessageToBackend: vi.fn() }));

describe('getXtmHubOrigin', () => {
  it('returns null when the XTM Hub URL is not set', () => {
    expect(getXtmHubOrigin(undefined)).toBeNull();
    expect(getXtmHubOrigin(null)).toBeNull();
    expect(getXtmHubOrigin('')).toBeNull();
  });

  it('returns null when the XTM Hub URL is not a valid absolute URL', () => {
    expect(getXtmHubOrigin('hub.filigran.io')).toBeNull();
    expect(getXtmHubOrigin('/app')).toBeNull();
  });

  it('keeps only the origin of the XTM Hub URL', () => {
    expect(getXtmHubOrigin('https://hub.filigran.io/app')).toBe('https://hub.filigran.io');
    expect(getXtmHubOrigin('https://hub.filigran.io/app/?x=1#y')).toBe('https://hub.filigran.io');
  });

  it('keeps the port and normalizes the host and default port', () => {
    expect(getXtmHubOrigin('http://localhost:3002/')).toBe('http://localhost:3002');
    expect(getXtmHubOrigin('HTTPS://Hub.Filigran.io:443/app')).toBe('https://hub.filigran.io');
  });
});

// The reported mismatches live for the page load, so each test uses its own origin.
describe('reportXtmHubOriginMismatch', () => {
  const HUB_ORIGIN = 'https://hub.filigran.io';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('logs the action, the origin it comes from and the configured origin', () => {
    reportXtmHubOriginMismatch('register', 'https://first.example', HUB_ORIGIN);

    expect(sendErrorMessageToBackend).toHaveBeenCalledExactlyOnceWith(
      'XTM Hub: "register" message ignored, it comes from "https://first.example" but the configured XTM Hub origin is "https://hub.filigran.io"',
    );
  });

  it('logs the same action from the same origin only once', () => {
    reportXtmHubOriginMismatch('register', 'https://repeat.example', HUB_ORIGIN);
    reportXtmHubOriginMismatch('register', 'https://repeat.example', HUB_ORIGIN);
    reportXtmHubOriginMismatch('register', 'https://repeat.example', HUB_ORIGIN);

    expect(sendErrorMessageToBackend).toHaveBeenCalledOnce();
  });

  it('logs again for another action or another origin', () => {
    reportXtmHubOriginMismatch('register', 'https://other-action.example', HUB_ORIGIN);
    reportXtmHubOriginMismatch('cancel', 'https://other-action.example', HUB_ORIGIN);
    reportXtmHubOriginMismatch('register', 'https://another-origin.example', HUB_ORIGIN);

    expect(sendErrorMessageToBackend).toHaveBeenCalledTimes(3);
  });
});
