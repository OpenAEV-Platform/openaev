import { simpleCall, simplePostCall } from '../../utils/Action';
import { type Option } from '../../utils/api-types';

const TAG_URI = '/api/tags';

export const searchTagAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall<Option[]>(`${TAG_URI}/options`, { params });
};

export const searchTagByIdAsOption = (ids: string[]) => {
  return simplePostCall<Option[]>(`${TAG_URI}/options`, ids);
};
