import { getReferential } from '../utils/Action';
import { arrayOfInjectors, injector } from './schemas';

export const fetchInjectors = () => (dispatch) => {
  const uri = '/api/injectors';
  return getReferential(arrayOfInjectors, uri)(dispatch);
};

export const fetchInjector = injectorId => (dispatch) => {
  const uri = `/api/injectors/${injectorId}`;
  return getReferential(injector, uri)(dispatch);
};
