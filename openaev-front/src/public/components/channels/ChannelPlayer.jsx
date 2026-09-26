import { Button } from '@filigran/design-system';
import { useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { Link, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchMe } from '../../../actions/Application';
import { fetchPlayerChannel } from '../../../actions/channels/channel-action';
import { fetchSimulationPlayerDocuments } from '../../../actions/Document';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { useHelper } from '../../../store';
import { useQueryParameter } from '../../../utils/Environment';
import useSimulationPermissions from '../../../utils/permissions/useSimulationPermissions';
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

const ChannelPlayer = () => {
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
    dispatch(fetchPlayerChannel(exerciseId, channelId, userId));
    dispatch(fetchSimulationPlayerDocuments(exerciseId, userId));
  }, []);
  if (channel) {
    return (
      <div className={classes.root}>
        {permissions.isLoggedIn && permissions.canAccess && (
          <Button
            asChild
            priority="secondary"
            style={{
              position: 'absolute',
              top: 20,
              right: 20,
            }}
          >
            <Link to={`/channels/${exerciseId}/${channelId}?article=${articleId}&user=${userId}&preview=true`}>
              {t('Switch to preview mode')}
            </Link>
          </Button>
        )}
        {permissions.isLoggedIn && permissions.canAccess && (
          <Button
            asChild
            priority="secondary"
            style={{
              position: 'absolute',
              top: 20,
              left: 20,
            }}
          >
            <Link to={`/admin/simulations/${exerciseId}/definition`}>
              {t('Back to administration')}
            </Link>
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

export default ChannelPlayer;
