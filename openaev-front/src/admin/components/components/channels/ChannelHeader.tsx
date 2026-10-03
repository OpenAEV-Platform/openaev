import { Chip } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
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

const ChannelHeader = () => {
  const { channelId } = useParams() as { channelId: Channel['channel_id'] };
  const { t } = useFormatter();
  const theme = useTheme();
  const { channel } = useHelper((helper: ChannelsHelper) => ({ channel: helper.getChannel(channelId) as Channel }));

  const mode = theme.palette.mode;
  const hasLogo = mode === 'dark' ? channel.channel_logo_dark : channel.channel_logo_light;
  // Brand accent per channel type; an untyped channel falls back to the theme accent.
  const typeColor = channel.channel_type ? ChannelColor(channel.channel_type) : theme.palette.primary.main;

  return (
    <DetailHero
      // A logo fills the square; the glyph fallback keeps the framed thumbnail.
      iconFills={!!hasLogo}
      iconNode={hasLogo
        ? (
            <img
              src={buildTenantApiPath(`/api/images/channels/id/${channelId}/${mode}`)}
              alt={channel.channel_name}
            />
          )
        : <ChannelIcon type={channel.channel_type} />}
      overline={t('Channel')}
      title={channel.channel_name ?? '-'}
      // The type qualifies the name and shares its line; the subtitle takes the next.
      chipsInline
      chips={<Chip label={t(channel.channel_type ?? 'Unknown')} color={typeColor} />}
      subtitle={channel.channel_description || undefined}
      action={<ChannelPopover channel={channel} variant="toggle" />}
    />
  );
};

export default ChannelHeader;
