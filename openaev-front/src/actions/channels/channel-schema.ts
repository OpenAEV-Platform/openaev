import { schema } from 'normalizr';

export const channelAction = new schema.Entity(
  'channels',
  {},
  { idAttribute: 'channel_id' },
);
export const arrayOfChannels = new schema.Array(channelAction);
