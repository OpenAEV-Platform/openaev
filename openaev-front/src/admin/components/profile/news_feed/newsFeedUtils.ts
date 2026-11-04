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
