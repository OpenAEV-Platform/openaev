import { type Dispatch } from 'redux';

import { getReferential } from '../../utils/Action';
import { arrayOfDocuments } from '../schemas';

// -- EXERCISES --

export const fetchExerciseDocuments = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/documents`;
  return getReferential<Document[]>(arrayOfDocuments, uri)(dispatch);
};

// -- SCENARIOS --

export const fetchScenarioDocuments = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/documents`;
  return getReferential<Document[]>(arrayOfDocuments, uri)(dispatch);
};
