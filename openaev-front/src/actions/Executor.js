import { getReferential } from '../utils/Action';
import * as schema from './Schema';

export const fetchExecutors = () => (dispatch) => {
  const uri = '/api/executors';
  return getReferential(schema.arrayOfExecutors, uri)(dispatch);
};

export default fetchExecutors();
