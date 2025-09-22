import { DevicesOtherOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type ReactElement } from 'react';
import { makeStyles } from 'tss-react/mui';

import AssetPlatformFragment from '../../../../components/common/list/fragments/AssetPlatformFragment';
import AssetTypeFragment from '../../../../components/common/list/fragments/AssetTypeFragment';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type EndpointOutput } from '../../../../utils/api-types';
import EndpointListItemFragments from '../../common/endpoints/EndpointListItemFragments';
import { type EndpointPopoverProps } from './EndpointPopover';

const useStyles = makeStyles()(() => ({
  item: { height: 50 },
  bodyItem: {
    fontSize: 13,
    float: 'left',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  typeChip: {
    height: 20,
    borderRadius: 4,
    textTransform: 'uppercase',
    width: 100,
    marginBottom: 5,
  },
}));

interface Props {
  endpoints: EndpointOutput[];
  renderActions: ((endpoint: EndpointOutput) => ReactElement<EndpointPopoverProps>);
  loading?: boolean;
  compact?: boolean;
}

const EndpointsList: FunctionComponent<Props> = ({
  endpoints,
  renderActions,
  loading = false,
  compact = false,
}) => {
  // Standard hooks
  const { classes } = useStyles();

  const component = (endpoint: EndpointOutput) => {
    return renderActions(endpoint);
  };

  const inlineStyles: Record<string, CSSProperties> = {
    asset_name: { width: compact ? '40%' : '30%' },
    asset_platform: { width: compact ? '10%' : '20%' },
    asset_tags: { width: '30%' },
    asset_type: { width: '20%' },
  };

  const headers = [
    {
      field: 'asset_name',
      label: 'Name',
      isSortable: true,
      value: (endpoint: EndpointOutput) => endpoint.asset_name,
    },
    {
      field: EndpointListItemFragments.ASSET_PLATFORM,
      label: 'Platform',
      isSortable: true,
      value: (endpoint: EndpointOutput) => <AssetPlatformFragment platform={endpoint.endpoint_platform} />,
    },
    {
      field: EndpointListItemFragments.ASSET_TAGS,
      label: 'Tags',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <ItemTags variant="list" tags={endpoint.asset_tags ?? []} />,
    },
    {
      field: EndpointListItemFragments.ASSET_TYPE,
      label: 'Type',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <AssetTypeFragment type={endpoint.asset_type} />,
    },
  ];

  if (loading) {
    return (
      <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
    );
  }
  if (endpoints == undefined || endpoints?.length == 0) {
    return null;
  }
  return (
    <List>
      { endpoints?.map((endpoint) => {
        return (
          <ListItem
            key={endpoint.asset_id}
            classes={{ root: classes.item }}
            divider={true}
            secondaryAction={component(endpoint)}
          >
            <ListItemIcon>
              <DevicesOtherOutlined color="primary" />
            </ListItemIcon>
            <ListItemText
              primary={(
                <>
                  {headers.map(header => (
                    <div
                      key={header.field}
                      className={classes.bodyItem}
                      style={inlineStyles[header.field]}
                    >
                      {header.value(endpoint)}
                    </div>
                  ))}
                </>
              )}
            />
          </ListItem>
        );
      })}
    </List>
  );
};

export default EndpointsList;
