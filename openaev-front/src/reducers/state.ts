import { Map as ImmutableMap, Record as ImmutableRecord } from 'immutable';

import { type Logged } from '../store';
import { entitiesInitializer } from './entities';

// Default application state
const initialState = {
  app: ImmutableRecord({ logged: ImmutableMap<keyof Logged, Logged[keyof Logged]>({}) })(),
  referential: entitiesInitializer,
};

export default initialState;
