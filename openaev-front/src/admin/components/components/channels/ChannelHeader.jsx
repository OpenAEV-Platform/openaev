import { Typography } from '@mui/material';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useHelper } from '../../../../store';
import ChannelPopover from './ChannelPopover';

const useStyles = makeStyles()(theme => ({
  container: {
    width: '100%',
    display: 'flex',
    alignItems: 'center',
    marginBottom: theme.spacing(2),
  },
  title: {
    float: 'left',
    textTransform: 'uppercase',
    margin: 0,
  },
}));

const ChannelHeader = () => {
  const { classes } = useStyles();
  const { channelId } = useParams();
  const { channel } = useHelper(helper => ({ channel: helper.getChannel(channelId) }));
  return (
    <div className={classes.container}>
      <Typography
        variant="h1"
        gutterBottom={true}
        classes={{ root: classes.title }}
      >
        {channel.channel_name}
      </Typography>
      <ChannelPopover channel={channel} />
    </div>
  );
};

export default ChannelHeader;
