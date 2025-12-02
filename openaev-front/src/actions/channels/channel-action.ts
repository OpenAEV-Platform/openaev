import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential } from '../../utils/Action';
import { type Channel, type ChannelCreateInput, type ChannelReader, type ChannelUpdateInput, type ChannelUpdateLogoInput, type Exercise, type Scenario, type User } from '../../utils/api-types';
import { arrayOfChannels, arrayOfDocuments, channel, channelReader } from '../schemas';

export const fetchChannels = () => (dispatch: Dispatch) => {
  const uri = '/api/channels';
  return getReferential<Channel[]>(arrayOfChannels, uri)(dispatch);
};
export const fetchChannel = (channelId: Channel['channel_id']) => (dispatch: Dispatch) => {
  const uri = `/api/channels/${channelId}`;
  return getReferential<Channel>(channel, uri)(dispatch);
};
export const updateChannel = (channelId: Channel['channel_id'], data: ChannelUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/channels/${channelId}`;
  return putReferential<Channel>(channel, uri, data)(dispatch);
};
export const updateChannelLogos = (channelId: Channel['channel_id'], data: ChannelUpdateLogoInput) => (dispatch: Dispatch) => {
  const uri = `/api/channels/${channelId}/logos`;
  return putReferential<Channel>(channel, uri, data)(dispatch);
};
export const addChannel = (data: ChannelCreateInput) => (dispatch: Dispatch) => postReferential<Channel>(channel, '/api/channels', data)(dispatch);
export const deleteChannel = (channelId: Channel['channel_id']) => (dispatch: Dispatch) => {
  const uri = `/api/channels/${channelId}`;
  return delReferential(uri, 'channels', channelId)(dispatch);
};

export const fetchPlayerChannel = (exerciseId: Exercise['exercise_id'], channelId: Channel['channel_id'], userId: User['user_id']) => (dispatch: Dispatch) => {
  const uri = `/api/player/channels/${exerciseId}/${channelId}?userId=${userId}`;
  return getReferential<ChannelReader>(channelReader, uri)(dispatch);
};
export const fetchObserverChannel = (exerciseId: Exercise['exercise_id'], channelId: Channel['channel_id']) => (dispatch: Dispatch) => {
  const uri = `/api/observer/channels/${exerciseId}/${channelId}`;
  return getReferential<ChannelReader>(channelReader, uri)(dispatch);
};

// -- SIMULATIONS --

export const fetchSimulationChannels = (simulationId: Exercise['exercise_id']) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${simulationId}/channels`;
  return getReferential<Channel[]>(arrayOfChannels, uri)(dispatch);
};

// -- SCENARIOS --

export const fetchScenarioChannels = (scenarioId: Scenario['scenario_id']) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/channels`;
  return getReferential<Channel[]>(arrayOfChannels, uri)(dispatch);
};

export const fetchDocumentsChannels = (channelId: Channel['channel_id']) => (dispatch: Dispatch) => getReferential<Document[]>(arrayOfDocuments, `/api/channels/${channelId}/documents`)(dispatch);
