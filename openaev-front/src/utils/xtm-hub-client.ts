import { sendErrorMessageToBackend } from './Action';
import { type PlatformSettings } from './api-types';
import { getUrl } from './utils';

export const getXtmHubOrigin = (hubUrl?: string | null): string | null => {
  if (!hubUrl) {
    return null;
  }
  try {
    return new URL(hubUrl).origin;
  } catch {
    return null;
  }
};

// A tab can repeat the same message: log each mismatch once per page load.
const reportedOriginMismatches = new Set<string>();

export const reportXtmHubOriginMismatch = (action: string, origin: string, expectedOrigin: string | null) => {
  const key = JSON.stringify([action, origin, expectedOrigin]);
  if (reportedOriginMismatches.has(key)) {
    return;
  }
  reportedOriginMismatches.add(key);
  sendErrorMessageToBackend(`XTM Hub: "${action}" message ignored, it comes from "${origin}" but the configured XTM Hub origin is "${expectedOrigin}"`);
};

interface FetchDocumentParams {
  settings: Pick<PlatformSettings, 'xtm_hub_url'>;
  serviceInstanceId: string;
  fileId: string;
  userPlatformToken: string;
}

const XtmHubClient = {
  fetchDocument: async ({ settings, serviceInstanceId, fileId, userPlatformToken }: FetchDocumentParams): Promise<File> => {
    const response = await fetch(
      getUrl(`/document/get/${serviceInstanceId}/${fileId}`, settings.xtm_hub_url ?? ''),
      {
        method: 'GET',
        credentials: 'omit',
        headers: { 'XTM-Hub-User-Platform-Token': userPlatformToken },
      },
    );

    const blob = await response.blob();
    return new File([blob], 'downloaded.zip', { type: 'application/zip' });
  },
};

export default XtmHubClient;
