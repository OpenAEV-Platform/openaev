import { type Channel } from '../../utils/api-types';

export interface ChannelsHelper {
  getChannel: (channelId: Channel['channel_id']) => Channel;
  getChannelsMap: () => Record<string, Channel>;
  getChannels: () => Channel[];
}
