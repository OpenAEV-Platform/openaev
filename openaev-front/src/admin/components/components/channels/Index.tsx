import { lazy } from 'react';
import { Route, Routes, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchChannel } from '../../../../actions/channels/channel-action';
import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { errorWrapper } from '../../../../components/Error';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import { type Channel as ChannelType } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import ChannelHeader from './ChannelHeader';

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const Channel = lazy(() => import('./Channel'));

const Index = () => {
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { channelId } = useParams() as { channelId: ChannelType['channel_id'] };
  const { channel } = useHelper((helper: ChannelsHelper) => ({ channel: helper.getChannel(channelId) }));
  useDataLoader(() => {
    dispatch(fetchChannel(channelId));
  });
  if (channel) {
    return (
      <div className={classes.root}>
        <ChannelHeader />
        <div className="clearfix" />
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
