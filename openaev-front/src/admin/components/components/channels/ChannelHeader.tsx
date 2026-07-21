import { CampaignOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useParams } from 'react-router';

import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { DetailHero } from '../../../../components/common/detail/EntityDetailCommon';
import { useHelper } from '../../../../store';
import { type Channel } from '../../../../utils/api-types';
import ChannelPopover from './ChannelPopover';

// Channel detail header, aligned on the shared DetailHero used by every other
// entity detail page (same icon box, title style and kebab sizing).
const ChannelHeader = () => {
  const { channelId } = useParams() as { channelId: Channel['channel_id'] };
  const { channel } = useHelper((helper: ChannelsHelper) => ({ channel: helper.getChannel(channelId) }));
  return (
    <Box sx={{ marginBottom: 2 }}>
      <DetailHero
        icon={CampaignOutlined}
        title={channel.channel_name}
        action={<ChannelPopover channel={channel} />}
      />
    </Box>
  );
};

export default ChannelHeader;
