import { Autocomplete, Box, TextField } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Channel } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { ArticleContext } from '../../common/Context';
import ChannelIcon from './ChannelIcon';
import { type ChannelOption } from './ChannelOption';

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
}));

interface Props {
  onChannelsChange: (value: ChannelOption[]) => void;
  onClearChannels?: () => void;
  fullWidth?: boolean;
}

interface ChannelTransformed {
  id: string;
  label: string;
  color: string;
  type: string;
}

const ChannelsFilter: FunctionComponent<Props> = (props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { fetchChannels } = useContext(ArticleContext);

  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchChannels());
  });

  const { channels } = useHelper((helper: ChannelsHelper) => ({ channels: helper.getChannels() }));
  const { onChannelsChange, onClearChannels = () => { }, fullWidth } = props;

  const channelColor = (type?: string) => {
    switch (type) {
      case 'newspaper':
        return '#3f51b5';
      case 'microblogging':
        return '#00bcd4';
      case 'tv':
        return '#ff9800';
      default:
        return '#ef41e1';
    }
  };
  const channelTransform = (n: Channel) => ({
    id: n.channel_id,
    label: n.channel_name,
    color: channelColor(n.channel_type),
    type: n.channel_type,
  });
  const channelsOptions: ChannelTransformed[] = channels.map(channelTransform);
  return (
    <div style={{
      display: 'flex',
      float: 'right',
    }}
    >
      <Autocomplete
        sx={{ width: fullWidth ? '100%' : 250 }}
        selectOnFocus={true}
        openOnFocus={true}
        autoSelect={false}
        autoHighlight={true}
        size="small"
        multiple
        options={channelsOptions}
        onChange={(event, value, reason) => {
          if (reason === 'clear') {
            onClearChannels();
          } else {
            onChannelsChange(value);
          }
        }}
        isOptionEqualToValue={(option, value: ChannelTransformed) => value === undefined || option.id === value.id}
        renderOption={(p, option) => (
          <Box component="li" {...p} key={option.id}>
            <div className={classes.icon} style={{ color: option.color }}>
              <ChannelIcon type={option.type} />
            </div>
            <div className={classes.text}>{option.label}</div>
          </Box>
        )}
        renderInput={params => (
          <TextField
            label={t('Channels')}
            variant="outlined"
            {...params}
          />
        )}
      />
    </div>
  );
};

export default ChannelsFilter;
