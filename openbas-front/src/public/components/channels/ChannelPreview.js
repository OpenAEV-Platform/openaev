import { Button } from '@mui/material';
import { useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { Link, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchMe } from '../../../actions/Application';
import { fetchObserverChannel } from '../../../actions/channels/channel-action';
import { fetchSimulationPlayerDocuments } from '../../../actions/Document';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { useHelper } from '../../../store';
import { useQueryParameter } from '../../../utils/Environment';
import useSimulationPermissions from '../../../utils/permissions/useSimulationPermissions.js';
import ChannelMicroblogging from './ChannelMicroblogging';
import ChannelNewspaper from './ChannelNewspaper';
import ChannelTvChannel from './ChannelTvChannel';

const useStyles = makeStyles()(() => ({
  root: {
    position: 'relative',
    flexGrow: 1,
    padding: 20,
  },
}));

const ChannelPreview = () => {
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const { t } = useFormatter();
  const [userId, articleId] = useQueryParameter(['user', 'article']);
  const { channelId, exerciseId } = useParams();
  const { channelReader } = useHelper(helper => ({ channelReader: helper.getChannelReader(channelId) }));
  const { channel_information: channel, channel_exercise: exercise } = channelReader ?? {};
  // Pass the full exercise because the exercise is never loaded in the store at this point
  const permissions = useSimulationPermissions(exerciseId, exercise);
  useEffect(() => {
    dispatch(fetchMe());
    dispatch(fetchObserverChannel(exerciseId, channelId));
    dispatch(fetchSimulationPlayerDocuments(exerciseId));
  }, []);
  if (channel) {
    return (
      <div className={classes.root}>
        {permissions.isLoggedIn && permissions.canAccess && (
          <Button
            color="secondary"
            variant="outlined"
            component={Link}
            to={`/channels/${exerciseId}/${channelId}?article=${articleId}&user=${userId}&preview=false`}
            style={{
              position: 'absolute',
              top: 20,
              right: 20,
            }}
          >
            {t('Switch to player mode')}
          </Button>
        )}
        {permissions.isLoggedIn && permissions.canAccess && (
          <Button
            color="primary"
            variant="outlined"
            component={Link}
            to={`/admin/simulations/${exerciseId}/definition`}
            style={{
              position: 'absolute',
              top: 20,
              left: 20,
            }}
          >
            {t('Back to administration')}
          </Button>
        )}
        {channel.channel_type === 'newspaper' && (
          <ChannelNewspaper channelReader={channelReader} />
        )}
        {channel.channel_type === 'microblogging' && (
          <ChannelMicroblogging channelReader={channelReader} />
        )}
        {channel.channel_type === 'tv' && (
          <ChannelTvChannel channelReader={channelReader} />
        )}
      </div>
    );
  }
  return <Loader />;
};

export default ChannelPreview;
