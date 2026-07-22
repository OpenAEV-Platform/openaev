import { HelpOutlineOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { type CSSProperties, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchAssetGroups } from '../../../../actions/asset_groups/assetgroup-action';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import ExportButton from '../../../../components/common/ExportButton';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { ASSET_GROUP_BASE_URL } from '../../../../constants/BaseUrls';
import { type AssetGroup, type AssetGroupOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import AssetGroupCreation from './AssetGroupCreation';
import AssetGroupPopover from './AssetGroupPopover';
import computeRuleValues from './assetGroupRules';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  asset_group_name: { width: '20%' },
  asset_group_description: { width: '20%' },
  asset_group_assets: { width: '35%' },
  asset_group_tags: { width: '25%' },
};

const AssetGroups = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t } = useFormatter();
  const navigate = useNavigate();

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const [searchId] = searchParams.getAll('id');

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'asset_group_name',
      label: 'Name',
      isSortable: true,
      value: (assetGroup: AssetGroupOutput) => assetGroup.asset_group_name,
    },
    {
      field: 'asset_group_description',
      label: 'Description',
      isSortable: true,
      value: (assetGroup: AssetGroupOutput) => assetGroup.asset_group_description || '-',
    },
    {
      field: 'asset_group_assets',
      label: 'Rules',
      isSortable: false,
      value: (assetGroup: AssetGroupOutput) =>
        computeRuleValues(assetGroup, t),
    },
    {
      field: 'asset_group_tags',
      label: 'Tags',
      isSortable: false,
      value: (assetGroup: AssetGroupOutput) => <ItemTags variant="list" tags={assetGroup.asset_group_tags} />,
    },
  ], []);

  const availableFilterNames = [
    'asset_group_name',
    'asset_group_description',
    'asset_group_tags',
  ];

  const [assetGroups, setAssetGroups] = useState<AssetGroup[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('asset-groups', buildSearchPagination({
    sorts: initSorting('asset_group_name'),
    textSearch: search,
  }));

  // Export
  const exportProps = {
    exportType: 'asset_group',
    exportKeys: [
      'asset_group_name',
      'asset_group_description',
      'asset_group_tags',
    ],
    exportData: assetGroups,
    exportFileName: `${t('AssetGroups')}.csv`,
  };

  const [loading, setLoading] = useState<boolean>(true);
  const searchAssetGroupsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchAssetGroups(input).finally(() => setLoading(false));
  };

  const onUpdateList = () => {
    searchAssetGroups(searchPaginationInput).then((result: { data: Page<AssetGroup> }) => {
      const { data } = result;
      setAssetGroups(data.content);
      queryableHelpers.paginationHelpers.handleChangeTotalElements(data.totalElements);
    });
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Asset groups'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={searchAssetGroupsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setAssetGroups}
        entityPrefix="asset_group"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSETS}>
              <AssetGroupCreation onCreate={result => setAssetGroups([result, ...assetGroups])} />
            </Can>
          </Box>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          style={{ paddingTop: 0 }}
          secondaryAction={<>&nbsp;</>}
        >
          <ListItemIcon />
          <ListItemText
            primary={(
              <SortHeadersComponentV2
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                sortHelpers={queryableHelpers.sortHelpers}
              />
            )}
          />
        </ListItem>
        {
          loading
            ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
            : assetGroups.map((assetGroup: AssetGroupOutput) => (
                <ListItem
                  key={assetGroup.asset_group_id}
                  divider
                  secondaryAction={(
                    <AssetGroupPopover
                      assetGroup={assetGroup}
                      onUpdate={onUpdateList}
                      onDelete={result => setAssetGroups(assetGroups.filter(ag => (ag.asset_group_id !== result)))}
                      openEditOnInit={assetGroup.asset_group_id === searchId}
                      onRemoveEndpointFromAssetGroup={assetId => setAssetGroups(assetGroups.map(ag => (ag.asset_group_id !== assetGroup.asset_group_id
                        ? ag
                        : {
                            ...ag,
                            asset_group_assets: ag?.asset_group_assets?.toSpliced(ag?.asset_group_assets?.indexOf(assetId), 1),
                          })))}
                    />
                  )}
                  disablePadding
                >
                  <ListItemButton
                    classes={{ root: classes.item }}
                    onClick={() => navigate(`${ASSET_GROUP_BASE_URL}/${assetGroup.asset_group_id}`)}
                  >
                    <ListItemIcon>
                      <SelectGroup color="primary" />
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div style={bodyItemsStyles.bodyItems}>
                          {headers.map(header => (
                            <div
                              key={header.field}
                              style={{
                                ...bodyItemsStyles.bodyItem,
                                ...inlineStyles[header.field],
                              }}
                            >
                              {header.value?.(assetGroup)}
                            </div>
                          ))}
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              ))
        }
      </List>
    </>
  );
};

export default AssetGroups;
