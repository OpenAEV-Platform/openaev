import { getReferential } from '../utils/Action';
import { arrayOfExecutors } from './schemas';

export const fetchExecutors = () => (dispatch) => {
  const uri = '/api/executors';
  return getReferential(arrayOfExecutors, uri)(dispatch);
};

export default fetchExecutors();
