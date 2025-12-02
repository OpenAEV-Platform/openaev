import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import { type Challenge, type ChallengeInput, type ChallengeResult, type ChallengeTryInput, type ScenarioChallengesReader, type SimulationChallengesReader } from '../utils/api-types';
import { arrayOfChallenges, arrayOfDocuments, challenge, scenarioChallengesReader, simulationChallengesReader } from './schemas';

export const fetchChallenges = () => (dispatch: Dispatch) => {
  const uri = '/api/challenges';
  return getReferential<Challenge[]>(arrayOfChallenges, uri)(dispatch);
};

export const findChallenges = (challengeIds: string[]) => (dispatch: Dispatch) => {
  const uri = '/api/challenges/find';
  return postReferential<Challenge[]>(arrayOfChallenges, uri, challengeIds)(dispatch);
};

export const fetchExerciseChallenges = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/challenges`;
  return getReferential<Challenge[]>(arrayOfChallenges, uri)(dispatch);
};

export const updateChallenge = (challengeId: string, data: ChallengeInput) => (dispatch: Dispatch) => {
  const uri = `/api/challenges/${challengeId}`;
  return putReferential<Challenge>(challenge, uri, data)(dispatch);
};

export const addChallenge = (data: ChallengeInput) => (dispatch: Dispatch) => postReferential<Challenge>(challenge, '/api/challenges', data)(dispatch);

export const tryChallenge = (challengeId: string, data: ChallengeTryInput) => {
  return simplePostCall<ChallengeResult>(`/api/challenges/${challengeId}/try`, data);
};

export const validateChallenge = (exerciseId: string, challengeId: string, userId: string, data: ChallengeTryInput) => (dispatch: Dispatch) => postReferential(
  simulationChallengesReader,
  `/api/player/challenges/${exerciseId}/${challengeId}/validate?userId=${userId}`,
  data,
)(dispatch);

export const deleteChallenge = (channelId: string) => (dispatch: Dispatch) => {
  const uri = `/api/challenges/${channelId}`;
  return delReferential(uri, 'challenges', channelId)(dispatch);
};

export const fetchSimulationPlayerChallenges = (simulationId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/player/simulations/${simulationId}/challenges?userId=${userId}`;
  return getReferential<SimulationChallengesReader>(simulationChallengesReader, uri)(dispatch);
};

export const fetchSimulationObserverChallenges = (simulationId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/observer/simulations/${simulationId}/challenges?userId=${userId}`;
  return getReferential<SimulationChallengesReader>(simulationChallengesReader, uri)(dispatch);
};

// -- SCENARIOS --

export const fetchScenarioChallenges = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/challenges`;
  return getReferential<Challenge[]>(arrayOfChallenges, uri)(dispatch);
};

export const fetchScenarioObserverChallenges = (scenarioId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/observer/scenarios/${scenarioId}/challenges?userId=${userId}`;
  return getReferential<ScenarioChallengesReader>(scenarioChallengesReader, uri)(dispatch);
};

// -- DOCUMENTS --
export const fetchDocumentsChallenge = (challengeId: string) => (dispatch: Dispatch) => {
  const uri = `/api/challenges/${challengeId}/documents`;
  return getReferential<Document[]>(arrayOfDocuments, uri)(dispatch);
};
