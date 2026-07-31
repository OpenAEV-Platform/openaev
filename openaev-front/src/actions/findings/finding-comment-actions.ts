import { simpleCall, simplePostCall } from '../../utils/Action';

const FINDING_COMMENTS_URI = (findingId: string) => `/api/findings/${findingId}/comments`;

export const fetchFindingComments = (findingId: string) => {
  return simpleCall(FINDING_COMMENTS_URI(findingId));
};

export const addFindingComment = (findingId: string, content: string) => {
  return simplePostCall(FINDING_COMMENTS_URI(findingId), { finding_comment_content: content });
};
