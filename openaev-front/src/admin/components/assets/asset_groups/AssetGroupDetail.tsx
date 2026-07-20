import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import { type ReactNode, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { fetchAssetGroup, searchEndpointsFromAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import { type AssetGroupsHelper } from '../../../../actions/asset_groups/assetgroup-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import AssetPlatformFragment from '../../../../components/common/list/fragments/AssetPlatformFragment';
import AssetTypeFragment from '../../../../components/common/list/fragments/AssetTypeFragment';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import { ASSET_BASE_URL, ASSET_GROUP_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type AssetOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import AssetGroupPopover from './AssetGroupPopover';
import computeRuleValues from './assetGroupRules';

const SECTION_LABEL_SX = {
  fontFamily: '"Geologica", sans-serif',
  fontWeight: 600,
  fontSize: 11,
  letterSpacing: '0.12em',
  textTransform: 'uppercase' as const,
  color: 'text.secondary',
  marginBottom: 1.5,
};

// A single labelled field inside an information section.
const Field = ({ label, children }: {
  label: string;
  children: ReactNode;
}) => (
  <div>
    <Typography variant="h3" gutterBottom sx={{ fontSize: 12 }}>{label}</Typography>
    <div>{children}</div>
  </div>
);

const AssetGroupDetail = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
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
    buildSearchPagination({}),
  );
  const availableFilterNames = [
    'endpoint_platform',
    'endpoint_arch',
    'asset_tags',
  ];

  if (!assetGroup) {
    return <Loader />;
  }

  const accent = theme.palette.primary.main;
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

      {/* Hero */}
      <Paper
        variant="outlined"
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          padding: 2,
          borderRadius: 1,
          background: `linear-gradient(135deg, ${alpha(accent, 0.08)}, transparent 60%)`,
        }}
      >
        <Box
          sx={{
            width: 52,
            height: 52,
            borderRadius: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
            backgroundColor: alpha(accent, 0.12),
            border: `1px solid ${alpha(accent, 0.3)}`,
          }}
        >
          <SelectGroup color="primary" />
        </Box>
        <Box sx={{
          minWidth: 0,
          flex: 1,
        }}
        >
          <Tooltip title={assetGroup.asset_group_name}>
            <Typography
              variant="h1"
              sx={{
                margin: 0,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {assetGroup.asset_group_name}
            </Typography>
          </Tooltip>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            marginTop: 0.5,
            flexWrap: 'wrap',
          }}
          >
            <Chip size="small" variant="outlined" label={`${memberCount} ${t('managed asset(s)')}`} sx={{ borderRadius: 1 }} />
          </Box>
        </Box>
        <AssetGroupPopover
          assetGroup={assetGroup}
          onUpdate={() => {
            dispatch(fetchAssetGroup(assetGroupId));
            setReloadContentCount(count => count + 1);
          }}
          onDelete={() => navigate(ASSET_GROUP_BASE_URL)}
        />
      </Paper>

      {/* Information */}
      <div>
        <Typography sx={SECTION_LABEL_SX}>{t('Information')}</Typography>
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderRadius: 1,
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: 2,
          }}
        >
          <Field label={t('Description')}>
            <ExpandableMarkdown source={assetGroup.asset_group_description} limit={300} />
          </Field>
          <Field label={t('Tags')}>
            <ItemTags variant="list" tags={assetGroup.asset_group_tags} />
          </Field>
        </Paper>
      </div>

      {/* Rules */}
      <div>
        <Typography sx={SECTION_LABEL_SX}>{t('Rules')}</Typography>
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderRadius: 1,
          }}
        >
          {computeRuleValues(assetGroup, t)}
        </Paper>
      </div>

      {/* Assets */}
      <div>
        <Typography sx={SECTION_LABEL_SX}>{t('Assets')}</Typography>
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
        {endpoints.length > 0 ? (
          <List>
            {endpoints.map(asset => (
              <ListItem key={asset.asset_id} divider disablePadding>
                <ListItemButton
                  component={Link}
                  to={`${ASSET_BASE_URL}/${asset.asset_id}`}
                >
                  <ListItemIcon>
                    <AssetPlatformFragment platform={asset.endpoint_platform} />
                  </ListItemIcon>
                  <ListItemText primary={asset.asset_name} />
                  <AssetTypeFragment type={asset.asset_type} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        ) : (
          <Empty message={t('No asset in this asset group.')} />
        )}
      </div>
    </Box>
  );
};

export default AssetGroupDetail;
