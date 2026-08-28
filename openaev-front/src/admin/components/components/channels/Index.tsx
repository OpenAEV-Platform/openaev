import { lazy } from 'react';
import { Route, Routes, useParams } from 'react-router';

import { fetchChannel } from '../../../../actions/channels/channel-action';
import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import { type Channel as ChannelType } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import ChannelHeader from './ChannelHeader';

const Channel = lazy(() => import('./Channel'));

const Index = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { channelId } = useParams() as { channelId: ChannelType['channel_id'] };
  const { channel } = useHelper((helper: ChannelsHelper) => ({ channel: helper.getChannel(channelId) }));
  useDataLoader(() => {
    dispatch(fetchChannel(channelId));
  });
  if (channel) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
      >
        <Breadcrumbs
          variant="object"
          elements={[
            { label: t('Components') },
            {
              label: t('Channels'),
              link: '/admin/components/channels',
            },
            {
              label: channel.channel_name,
              current: true,
            },
          ]}
        />
        <ChannelHeader />
        <Routes>
          <Route path="" element={errorWrapper(Channel)()} />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </div>
    );
  }
  return <Loader />;
};

export default Index;
