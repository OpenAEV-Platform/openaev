import { simpleDelCall, simplePostCall } from '../../utils/Action';
import { type PlayerBulkProcessingInput, type SearchPaginationInput } from '../../utils/api-types';

const PLAYER_URI = '/api/players';

export const searchPlayers = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${PLAYER_URI}/search`;
  return simplePostCall(uri, data);
};

export const bulkDeletePlayers = (input: PlayerBulkProcessingInput) => {
  return simpleDelCall(PLAYER_URI, { data: input });
};
