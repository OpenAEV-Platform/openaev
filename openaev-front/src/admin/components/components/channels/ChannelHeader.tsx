import { Chip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { DetailHero } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';
import ChannelColor from '../../../../public/components/channels/ChannelColor';
import { useHelper } from '../../../../store';
import { type Channel } from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import ChannelIcon from './ChannelIcon';
import ChannelPopover from './ChannelPopover';

// Channel detail header, aligned on the shared DetailHero used by every other
// entity detail page (same icon box, title style and kebab sizing).
const ChannelHeader = () => {
  const { channelId } = useParams() as { channelId: Channel['channel_id'] };
  const { t } = useFormatter();
  const theme = useTheme();
  const { channel } = useHelper((helper: ChannelsHelper) => ({ channel: helper.getChannel(channelId) as Channel }));

  const mode = theme.palette.mode;
  const hasLogo = mode === 'dark' ? channel.channel_logo_dark : channel.channel_logo_light;
  // Brand accent per channel type, from the shared ChannelColor palette (single
  // source of truth also backing the public channel pages); a channel without a
  // type falls back to the theme accent.
  const typeColor = channel.channel_type ? ChannelColor(channel.channel_type) : theme.palette.primary.main;

  return (
    <DetailHero
      iconNode={hasLogo
        ? (
            <img
              src={buildTenantApiPath(`/api/images/channels/id/${channelId}/${mode}`)}
              alt={channel.channel_name}
              style={{
                maxWidth: 36,
                maxHeight: 36,
                objectFit: 'contain',
              }}
            />
          )
        : <ChannelIcon type={channel.channel_type} />}
      overline={t('Channel')}
      title={channel.channel_name ?? '-'}
      chips={(
        <Chip
          size="small"
          variant="outlined"
          label={t(channel.channel_type ?? 'Unknown')}
          sx={{
            height: 22,
            fontSize: 11,
            borderRadius: 1,
            color: typeColor,
            borderColor: alpha(typeColor, 0.5),
            backgroundColor: alpha(typeColor, 0.08),
          }}
        />
      )}
      action={<ChannelPopover channel={channel} />}
      footer={channel.channel_description
        ? (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {channel.channel_description}
            </Typography>
          )
        : undefined}
    />
  );
};

export default ChannelHeader;
