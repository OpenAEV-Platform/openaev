import { HelpOutlineOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type ReactElement } from 'react';
import { makeStyles } from 'tss-react/mui';

import AssetPlatformFragment from '../../../../components/common/list/fragments/AssetPlatformFragment';
import AssetTypeFragment from '../../../../components/common/list/fragments/AssetTypeFragment';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type AssetOutput } from '../../../../utils/api-types';
import EndpointListItemFragments from '../../common/endpoints/EndpointListItemFragments';
import AssetCategoryIcon from '../AssetCategoryIcon';
import { type AssetPopoverProps } from './AssetPopover';

// Header labels are still rendered without sort handles when no sortHelpers
// are provided (client-side lists).
const NOOP_SORT_HELPERS: SortHelpers = {
  handleSort: () => {},
  handleDirectedSort: () => {},
  getSortBy: () => '',
  getSortAsc: () => true,
};

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
  /** Render a column headers row above the list. */
  withHeaders?: boolean;
  /** Enables clickable column sorting (pass `queryableHelpers.sortHelpers`). */
  sortHelpers?: SortHelpers;
}

// Generic over AssetOutput so asset groups (which can hold any asset type: endpoints, AI targets,
// identities, cloud / web / network assets, ...) list uniformly. Endpoint-specific screens still
// pass the richer EndpointOutput, inferred as T.
const AssetsList = <T extends AssetOutput>({
  endpoints,
  renderActions,
  loading = false,
  compact = false,
  withHeaders = false,
  sortHelpers,
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

  const headersRow = withHeaders && (
    <ListItem
      dense
      divider
      secondaryAction={<span>&nbsp;</span>}
    >
      <ListItemIcon />
      <ListItemText
        primary={(
          <SortHeadersComponentV2
            headers={headers.map(header => ({
              field: header.field,
              label: header.label,
              isSortable: header.isSortable && !!sortHelpers,
            }))}
            inlineStylesHeaders={inlineStyles}
            sortHelpers={sortHelpers ?? NOOP_SORT_HELPERS}
          />
        )}
      />
    </ListItem>
  );

  if (loading) {
    return (
      <>
        {headersRow && <List>{headersRow}</List>}
        <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
      </>
    );
  }
  if (endpoints == undefined || endpoints?.length == 0) {
    return null;
  }
  return (
    <List>
      {headersRow}
      { endpoints?.map((asset) => {
        return (
          <ListItem
            key={asset.asset_id}
            classes={{ root: classes.item }}
            divider={true}
            secondaryAction={component(asset)}
          >
            <ListItemIcon>
              {/* Same category-aware glyph as the assets inventory page, so a web app,
                  cloud resource or AI target never shows the generic device icon. */}
              <AssetCategoryIcon category={asset.asset_category} color="primary" />
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
