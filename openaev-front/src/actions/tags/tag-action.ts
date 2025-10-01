import { simpleCall, simplePostCall } from '../../utils/Action';

const TAG_URI = '/api/tags';

export const searchTagAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${TAG_URI}/options`, { params });
};

export const searchTagByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${TAG_URI}/options`, ids);
};
