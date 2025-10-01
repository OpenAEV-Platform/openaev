import { combineReducers } from 'redux';

import app from './App';
import referential from './Referential';

const createRootReducer = () => combineReducers({
  app,
  referential,
});
export default createRootReducer;
