import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { type CSSProperties, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { fetchAssetGroup, searchEndpointsFromAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import { type AssetGroupsHelper } from '../../../../actions/asset_groups/assetgroup-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, Field, InformationGrid, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import AssetPlatformFragment from '../../../../components/common/list/fragments/AssetPlatformFragment';
import AssetTypeFragment from '../../../../components/common/list/fragments/AssetTypeFragment';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemCriticality from '../../../../components/ItemCriticality';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import { ASSET_BASE_URL, ASSET_GROUP_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type AssetOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import AssetGroupPopover from './AssetGroupPopover';
import computeRuleValues from './assetGroupRules';

const inlineStyles: Record<string, CSSProperties> = {
  asset_name: { width: '40%' },
  asset_type: { width: '20%' },
  asset_criticality: { width: '20%' },
  asset_tags: { width: '20%' },
};

const AssetGroupDetail = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const bodyItemsStyles = useBodyItemsStyles();
  const { assetGroupId } = useParams() as { assetGroupId: string };

  // Fetching data
  const { assetGroup } = useHelper((helper: AssetGroupsHelper) => ({ assetGroup: helper.getAssetGroup(assetGroupId) }));
  useDataLoader(() => {
    dispatch(fetchAssetGroup(assetGroupId));
  }, [assetGroupId]);

  // Member assets pagination
  const [endpoints, setEndpoints] = useState<AssetOutput[]>([]);
  const [reloadContentCount, setReloadContentCount] = useState(0);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'asset-group-detail-assets',
    buildSearchPagination({ sorts: initSorting('asset_name') }),
  );
  const availableFilterNames = [
    'endpoint_platform',
    'endpoint_arch',
    'asset_tags',
  ];

  const headers: Header[] = [
    {
      field: 'asset_name',
      label: 'Name',
      isSortable: true,
      value: (asset: AssetOutput) => asset.asset_name,
    },
    {
      field: 'asset_type',
      label: 'Type',
      isSortable: false,
      value: (asset: AssetOutput) => <AssetTypeFragment type={asset.asset_type} category={asset.asset_category} />,
    },
    {
      field: 'asset_criticality',
      label: 'Criticality',
      isSortable: false,
      value: (asset: AssetOutput) => <ItemCriticality criticality={asset.asset_criticality} />,
    },
    {
      field: 'asset_tags',
      label: 'Tags',
      isSortable: false,
      value: (asset: AssetOutput) => <ItemTags variant="list" tags={asset.asset_tags} />,
    },
  ];

  if (!assetGroup) {
    return <Loader />;
  }

  const memberCount = assetGroup.asset_group_assets?.length ?? 0;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Asset groups'),
            link: ASSET_GROUP_BASE_URL,
          },
          {
            label: assetGroup.asset_group_name,
            current: true,
          },
        ]}
      />

      <DetailHero
        icon={SelectGroup}
        title={assetGroup.asset_group_name}
        chips={(
          <Box
            component="span"
            sx={{
              fontSize: 13,
              color: 'text.secondary',
            }}
          >
            {t('{count} managed assets', { count: memberCount })}
          </Box>
        )}
        action={(
          <AssetGroupPopover
            assetGroup={assetGroup}
            onUpdate={() => {
              dispatch(fetchAssetGroup(assetGroupId));
              setReloadContentCount(count => count + 1);
            }}
            onDelete={() => navigate(ASSET_GROUP_BASE_URL)}
          />
        )}
      />

      <InformationGrid title={t('Information')}>
        <Field label={t('Description')}>
          {assetGroup.asset_group_description
            ? <ExpandableMarkdown source={assetGroup.asset_group_description} limit={300} />
            : '-'}
        </Field>
        <Field label={t('Tags')}>
          <ItemTags variant="list" tags={assetGroup.asset_group_tags} />
        </Field>
      </InformationGrid>

      <SectionBlock title={t('Rules')}>
        {computeRuleValues(assetGroup, t)}
      </SectionBlock>

      <SectionBlock title={t('Assets')} disablePadding>
        <Box sx={{
          padding: 2,
          paddingBottom: 0,
        }}
        >
          <PaginationComponentV2
            fetch={(input: SearchPaginationInput) => searchEndpointsFromAssetGroup(input, assetGroupId)}
            searchPaginationInput={searchPaginationInput}
            setContent={setEndpoints}
            entityPrefix="endpoint"
            availableFilterNames={availableFilterNames}
            queryableHelpers={queryableHelpers}
            reloadContentCount={reloadContentCount}
            contextId={assetGroupId}
          />
        </Box>
        <List disablePadding>
          <ListItem style={{
            paddingTop: 0,
            textTransform: 'uppercase',
          }}
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
          {endpoints.length > 0
            ? endpoints.map(asset => (
                <ListItem key={asset.asset_id} divider disablePadding data-testid="asset-group-asset-row">
                  <ListItemButton
                    component={Link}
                    to={`${ASSET_BASE_URL}/${asset.asset_id}`}
                    sx={{ height: 50 }}
                  >
                    <ListItemIcon>
                      <AssetPlatformFragment platform={asset.endpoint_platform} />
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
                              {header.value?.(asset)}
                            </div>
                          ))}
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              ))
            : <Empty message={t('No asset in this asset group.')} />}
        </List>
      </SectionBlock>
    </Box>
  );
};

export default AssetGroupDetail;
