import { XTM_HUB_DEFAULT_URL } from '../../../../utils/Environment';

// The path comes from XTM Hub data: only a link that stays on the XTM Hub origin is rendered.
export const buildHubUrl = (urlPath: string, hubUrl: string): string | null => {
  try {
    const base = new URL(hubUrl);
    const url = new URL(urlPath, base);
    const isWeb = url.protocol === 'https:' || url.protocol === 'http:';
    return isWeb && url.origin === base.origin ? url.toString() : null;
  } catch {
    return null;
  }
};

/** The XTM Hub page of a path, on the XTM Hub configured on the platform or the default one. */
export const buildConfiguredHubUrl = (urlPath: string | undefined, configuredHubUrl: string | undefined): string | null => (
  urlPath ? buildHubUrl(urlPath, configuredHubUrl ?? XTM_HUB_DEFAULT_URL) : null
);
