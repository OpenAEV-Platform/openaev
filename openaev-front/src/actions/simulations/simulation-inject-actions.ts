import type { Dispatch } from 'redux';

import { postReferential } from '../../utils/Action';
import type { Exercise, InjectInput } from '../../utils/api-types';
import * as schema from '../Schema';

export const createInjectsForSimulation = (simulationId: Exercise['exercise_id'], inputs: InjectInput[]) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${simulationId}/injects/bulk`;
  return postReferential(schema.arrayOfInjects, uri, inputs)(dispatch);
};
