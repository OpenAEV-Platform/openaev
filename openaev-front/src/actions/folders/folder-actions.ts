import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type FolderInput } from '../../utils/api-types';

const FOLDER_URI = '/api/folders';

export const fetchFolders = () => {
  return simpleCall(FOLDER_URI);
};

export const createFolder = (data: FolderInput) => {
  return simplePostCall(FOLDER_URI, data);
};

export const updateFolder = (folderId: string, data: FolderInput) => {
  return simplePutCall(`${FOLDER_URI}/${folderId}`, data);
};

export const deleteFolder = (folderId: string) => {
  return simpleDelCall(`${FOLDER_URI}/${folderId}`);
};

export const moveDocumentToFolder = (documentId: string, folderId: string | null) => {
  const query = folderId ? `?folderId=${folderId}` : '';
  return simplePutCall(`/api/documents/${documentId}/folder${query}`, {});
};
