import { simplePostCall } from '../../utils/Action';
import { type SearchPaginationInput } from '../../utils/api-types';

const PLAYER_URI = '/api/players';

// eslint-disable-next-line import/prefer-default-export
export const searchPlayers = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${PLAYER_URI}/search`;
  return simplePostCall(uri, data);
};
