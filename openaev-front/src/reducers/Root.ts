import { combineReducers, type Reducer } from 'redux';

import { type Actions, type StoreState } from '../store';
import app from './app';
import referential from './Referential';

const createRootReducer = () => combineReducers({
  app,
  referential,
}) as unknown as Reducer<StoreState, Actions, StoreState>;

export default createRootReducer;
