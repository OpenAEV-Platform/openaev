import { HelpOutlineOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import {
  type CSSProperties,
  type FunctionComponent,
  type ReactElement,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AssetGroupsHelper } from '../../../../actions/asset_groups/assetgroup-helper';
import { type Header } from '../../../../components/common/SortHeadersList';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { useHelper } from '../../../../store';
import { type AssetGroupOutput } from '../../../../utils/api-types';
import { EndpointContext } from '../../../../utils/context/endpoint/EndpointContext';
import type { EndpointPopoverProps } from '../endpoints/EndpointPopover';

const useStyles = makeStyles()(() => ({
  item: { height: 50 },
  bodyItem: {
    fontSize: 13,
    float: 'left',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  asset_group_name: { width: '50%' },
  asset_group_tags: { width: '50%' },
};

interface Props {
  assetGroupIds: string[];
  renderActions: ((endpoint: AssetGroupOutput) => ReactElement<EndpointPopoverProps>);
}

const AssetGroupsList: FunctionComponent<Props> = ({
  assetGroupIds = [],
  renderActions,
}) => {
  const { classes } = useStyles();

  const [loading, setLoading] = useState<boolean>(true);
  const [assetGroupValues, setAssetGroupValues] = useState<AssetGroupOutput[]>([]);
  const { fetchAssetGroupsByIds } = useContext(EndpointContext);
  const { assetGroupMaps } = useHelper((helper: AssetGroupsHelper) => ({ assetGroupMaps: helper.getAssetGroupMaps() }));

  const component = (assetGroup: AssetGroupOutput) => {
    return renderActions(assetGroup);
  };

  useEffect(() => {
    setLoading(true);
    const assetGroups = assetGroupIds.map(id => assetGroupMaps[id]).filter(e => e !== undefined) as AssetGroupOutput[];
    const missingIds = assetGroupIds.filter(id => !assetGroupMaps[id]);

    if (missingIds.length > 0) {
      fetchAssetGroupsByIds(missingIds).then((result) => {
        setAssetGroupValues([...result.data, ...assetGroups]);
        setLoading(false);
      });
    } else {
      setAssetGroupValues(assetGroups);
      setLoading(false);
    }
  }, [assetGroupIds]);

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'asset_group_name',
      label: 'Asset Group Name',
      isSortable: false,
      value: (assetGroup: AssetGroupOutput) => assetGroup.asset_group_name,
    },
    {
      field: 'asset_group_tags',
      label: 'tags',
      isSortable: false,
      value: (assetGroup: AssetGroupOutput) => <ItemTags variant="reduced-view" tags={assetGroup.asset_group_tags} />,
    },
  ], []);

  const isLoading = loading && assetGroupIds.length > 0;

  if (isLoading) {
    return (
      <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
    );
  }
  if (assetGroupValues == undefined || assetGroupValues?.length == 0) {
    return null;
  }
  return (
    <>
      <List>
        {assetGroupValues?.map((assetGroup) => {
          return (
            <ListItem
              key={assetGroup.asset_group_id}
              classes={{ root: classes.item }}
              divider
              secondaryAction={component(assetGroup)}
            >
              <ListItemIcon>
                <SelectGroup color="primary" />
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
                        {header.value?.(assetGroup)}
                      </div>
                    ))}
                  </>
                )}
              />
            </ListItem>
          );
        })}
      </List>
    </>
  );
};

export default AssetGroupsList;
