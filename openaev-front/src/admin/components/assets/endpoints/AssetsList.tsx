import { DevicesOtherOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type ReactElement } from 'react';
import { makeStyles } from 'tss-react/mui';

import AssetPlatformFragment from '../../../../components/common/list/fragments/AssetPlatformFragment';
import AssetTypeFragment from '../../../../components/common/list/fragments/AssetTypeFragment';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type AssetOutput } from '../../../../utils/api-types';
import EndpointListItemFragments from '../../common/endpoints/EndpointListItemFragments';
import { type AssetPopoverProps } from './AssetPopover';

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

interface Props<T extends AssetOutput> {
  endpoints: T[];
  renderActions: ((asset: T) => ReactElement<AssetPopoverProps>);
  loading?: boolean;
  compact?: boolean;
}

// Generic over AssetOutput so asset groups (which can hold any asset type: endpoints, AI targets,
// identities, cloud / web / network assets, ...) list uniformly. Endpoint-specific screens still
// pass the richer EndpointOutput, inferred as T.
const AssetsList = <T extends AssetOutput>({
  endpoints,
  renderActions,
  loading = false,
  compact = false,
}: Props<T>) => {
  // Standard hooks
  const { classes } = useStyles();

  const component = (asset: T) => {
    return renderActions(asset);
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
      value: (asset: T) => asset.asset_name,
    },
    {
      field: EndpointListItemFragments.ASSET_PLATFORM,
      label: 'Platform',
      isSortable: true,
      value: (asset: T) => <AssetPlatformFragment platform={asset.endpoint_platform} />,
    },
    {
      field: EndpointListItemFragments.ASSET_TAGS,
      label: 'Tags',
      isSortable: false,
      value: (asset: T) => <ItemTags variant="list" tags={asset.asset_tags ?? []} />,
    },
    {
      field: EndpointListItemFragments.ASSET_TYPE,
      label: 'Type',
      isSortable: false,
      value: (asset: T) => <AssetTypeFragment type={asset.asset_type} category={asset.asset_category} />,
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
      { endpoints?.map((asset) => {
        return (
          <ListItem
            key={asset.asset_id}
            classes={{ root: classes.item }}
            divider={true}
            secondaryAction={component(asset)}
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
                      {header.value(asset)}
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

export default AssetsList;
