import { getReferential } from '../utils/Action';
import * as schema from './Schema';

export const fetchInjectors = () => (dispatch) => {
  const uri = '/api/injectors';
  return getReferential(schema.arrayOfInjectors, uri)(dispatch);
};

export const fetchInjector = injectorId => (dispatch) => {
  const uri = `/api/injectors/${injectorId}`;
  return getReferential(schema.injector, uri)(dispatch);
};
