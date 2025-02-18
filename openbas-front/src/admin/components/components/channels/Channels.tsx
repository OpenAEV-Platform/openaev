import { ChevronRightOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { type CSSProperties } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchChannels } from '../../../../actions/channels/channel-action';
import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { type UserHelper } from '../../../../actions/helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { type Channel } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchAnFilter from '../../../../utils/SortingFiltering';
import ChannelIcon from './ChannelIcon';
import CreateChannel from './CreateChannel';

const useStyles = makeStyles()(() => ({
  parameters: {
    marginTop: -10,
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    height: 52,
  },
  filters: {
    display: 'flex',
    gap: '10px',
  },
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
    height: 40,
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  bodyItem: {
    height: 20,
    fontSize: 13,
  },
}));

const headerStyles: Record<string, CSSProperties> = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  channel_type: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '700',
  },
  channel_name: {
    float: 'left',
    width: '25%',
    fontSize: 12,
    fontWeight: '700',
  },
  channel_description: {
    float: 'left',
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles: Record<string, CSSProperties> = {
  channel_type: {
    float: 'left',
    width: '15%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  channel_name: {
    float: 'left',
    width: '25%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  channel_description: {
    float: 'left',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

const Channels = () => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  // Filter and sort hook
  const searchColumns = ['type', 'name', 'description'];
  const filtering = useSearchAnFilter('channel', 'name', searchColumns);
  // Fetching data
  const { channels, userAdmin }: {
    channels: Channel[];
    userAdmin: boolean;
  } = useHelper((helper: ChannelsHelper & UserHelper) => ({
    channels: helper.getChannels(),
    userAdmin: helper.getMe()?.user_admin ?? false,
  }));
  useDataLoader(() => {
    dispatch(fetchChannels());
  });
  const sortedChannels: Channel[] = filtering.filterAndSort(channels);
  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Components') }, {
          label: t('Channels'),
          current: true,
        }]}
      />
      <div className={classes.parameters}>
        <div className={classes.filters}>
          <SearchFilter
            variant="small"
            onChange={filtering.handleSearch}
            keyword={filtering.keyword}
          />
        </div>
      </div>
      <div className="clearfix" />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
        >
          <ListItemIcon>
            <span
              style={{
                padding: '0 8px 0 8px',
                fontWeight: 700,
                fontSize: 12,
              }}
            >
            &nbsp;
            </span>
          </ListItemIcon>
          <ListItemText
            primary={(
              <div>
                {filtering.buildHeader(
                  'channel_type',
                  'Type',
                  true,
                  headerStyles,
                )}
                {filtering.buildHeader(
                  'channel_name',
                  'Name',
                  true,
                  headerStyles,
                )}
                {filtering.buildHeader(
                  'channel_description',
                  'Subtitle',
                  true,
                  headerStyles,
                )}
              </div>
            )}
          />
          <ListItemSecondaryAction>&nbsp;</ListItemSecondaryAction>
        </ListItem>
        {sortedChannels.map(channel => (
          <ListItemButton
            key={channel.channel_id}
            classes={{ root: classes.item }}
            divider
            component={Link}
            to={`/admin/components/channels/${channel.channel_id}`}
          >
            <ListItemIcon>
              <ChannelIcon
                type={channel.channel_type}
                tooltip={t(channel.channel_type || 'Unknown')}
              />
            </ListItemIcon>
            <ListItemText
              primary={(
                <div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.channel_type}
                  >
                    {t(channel.channel_type || 'Unknown')}
                  </div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.channel_name}
                  >
                    {channel.channel_name}
                  </div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.channel_description}
                  >
                    {channel.channel_description}
                  </div>
                </div>
              )}
            />
            <ListItemSecondaryAction>
              <ChevronRightOutlined />
            </ListItemSecondaryAction>
          </ListItemButton>
        ))}
      </List>
      {userAdmin && <CreateChannel />}
    </>
  );
};

export default Channels;
