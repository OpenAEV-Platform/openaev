import type { Dispatch } from 'redux';

import { postReferential } from '../../utils/Action';
import type { InjectInput, Scenario } from '../../utils/api-types';
import * as schema from '../Schema';

export const createInjectsForScenario = (scenarioId: Scenario['scenario_id'], inputs: InjectInput[]) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/bulk`;
  return postReferential(schema.arrayOfInjects, uri, inputs)(dispatch);
};
