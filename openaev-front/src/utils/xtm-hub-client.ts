import { getUrl } from './utils';

interface FetchDocumentParams {
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-expect-error
  settings;
  serviceInstanceId: string;
  fileId: string;
  userPlatformToken: string;
}

const XtmHubClient = {
  fetchDocument: async ({ settings, serviceInstanceId, fileId, userPlatformToken }: FetchDocumentParams): Promise<File> => {
    const response = await fetch(
      getUrl(`/document/get/${serviceInstanceId}/${fileId}`, settings.xtm_hub_url),
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
