import { schema } from 'normalizr';
import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import { type ChallengeInput, type ChallengeTryInput } from '../utils/api-types';
import { arrayOfDocuments, scenarioChallengesReaders, simulationChallengesReaders } from './Schema';

const challenge = new schema.Entity(
  'challenges',
  {},
  { idAttribute: 'challenge_id' },
);
const arrayOfChallenges = new schema.Array(challenge);

export const fetchChallenges = () => (dispatch: Dispatch) => {
  const uri = '/api/challenges';
  return getReferential(arrayOfChallenges, uri)(dispatch);
};

export const findChallenges = (challengeIds: string[]) => (dispatch: Dispatch) => {
  const uri = '/api/challenges/find';
  return postReferential(arrayOfChallenges, uri, challengeIds)(dispatch);
};

export const fetchExerciseChallenges = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/challenges`;
  return getReferential(arrayOfChallenges, uri)(dispatch);
};

export const updateChallenge = (challengeId: string, data: ChallengeInput) => (dispatch: Dispatch) => {
  const uri = `/api/challenges/${challengeId}`;
  return putReferential(challenge, uri, data)(dispatch);
};

export const addChallenge = (data: ChallengeInput) => (dispatch: Dispatch) => postReferential(challenge, '/api/challenges', data)(dispatch);

export const tryChallenge = (challengeId: string, data: ChallengeTryInput) => {
  return simplePostCall(`/api/challenges/${challengeId}/try`, data);
};

export const validateChallenge = (exerciseId: string, challengeId: string, userId: string, data: ChallengeTryInput) => (dispatch: Dispatch) => postReferential(
  simulationChallengesReaders,
  `/api/player/challenges/${exerciseId}/${challengeId}/validate?userId=${userId}`,
  data,
)(dispatch);

export const deleteChallenge = (channelId: string) => (dispatch: Dispatch) => {
  const uri = `/api/challenges/${channelId}`;
  return delReferential(uri, 'challenges', channelId)(dispatch);
};

export const fetchSimulationPlayerChallenges = (simulationId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/player/simulations/${simulationId}/challenges?userId=${userId}`;
  return getReferential(simulationChallengesReaders, uri)(dispatch);
};

export const fetchSimulationObserverChallenges = (simulationId: string, userId: string | null) => (dispatch: Dispatch) => {
  const uri = `/api/observer/simulations/${simulationId}/challenges?userId=${userId}`;
  return getReferential(simulationChallengesReaders, uri)(dispatch);
};

// -- SCENARIOS --

export const fetchScenarioChallenges = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/challenges`;
  return getReferential(arrayOfChallenges, uri)(dispatch);
};

export const fetchScenarioObserverChallenges = (scenarioId: string, userId: string | null) => (dispatch: Dispatch) => {
  const uri = `/api/observer/scenarios/${scenarioId}/challenges?userId=${userId}`;
  return getReferential(scenarioChallengesReaders, uri)(dispatch);
};

// -- DOCUMENTS --
export const fetchDocumentsChallenge = (challengeId: string) => (dispatch: Dispatch) => {
  const uri = `/api/challenges/${challengeId}/documents`;
  return getReferential(arrayOfDocuments, uri)(dispatch);
};
