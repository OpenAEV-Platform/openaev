import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { InfoOutlined } from '@mui/icons-material';
import { InputLabel } from '@mui/material';
import { type FunctionComponent } from 'react';

import FilterField from '../../../../components/common/queryable/filter/FilterField';
import { emptyFilterGroup } from '../../../../components/common/queryable/filter/FilterUtils';
import useFiltersState from '../../../../components/common/queryable/filter/useFiltersState';
import { useFormatter } from '../../../../components/i18n';
import { type FilterGroup } from '../../../../utils/api-types';

interface Props {
  value?: FilterGroup;
  onChange?: (value: FilterGroup) => void;
  contextId?: string;
}

const DynamicAssetField: FunctionComponent<Props> = ({
  value,
  onChange,
  contextId,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const [filterGroup, helpers] = useFiltersState(value ?? emptyFilterGroup, undefined, onChange);

  // Every filterable attribute an asset group rule can resolve on the backend
  // (AssetGroupService.computeDynamicAssets): base asset fields, then endpoint-only ones.
  const availableFilterNames = [
    'asset_tags',
    'asset_name',
    'asset_description',
    'asset_external_reference',
    'asset_category',
    'asset_subcategory',
    'asset_criticality',
    'asset_status',
    'asset_internet_facing',
    'asset_cloud_provider',
    'asset_cloud_region',
    'asset_cloud_native_type',
    'asset_hostname',
    'asset_ips',
    'asset_seen_ip',
    'asset_mac_addresses',
    'asset_url',
    'endpoint_platform',
    'endpoint_arch',
  ];

  return (
    <div style={{ marginTop: 20 }}>
      <div style={{
        display: 'flex',
        alignItems: 'end',
        gap: 10,
      }}
      >
        <InputLabel id="dynamic-asset-filter">{t('Rule')}</InputLabel>
        <Tooltip>
          <TooltipTrigger asChild>
            <InfoOutlined
              fontSize="small"
              color="primary"
              style={{ marginTop: 8 }}
            />
          </TooltipTrigger>
          <TooltipContent>{t('Filter allowing assets to be added dynamically to this group')}</TooltipContent>
        </Tooltip>
      </div>
      <FilterField
        entityPrefix="endpoint"
        availableFilterNames={availableFilterNames}
        filterGroup={filterGroup}
        helpers={helpers}
        style={{ marginTop: 20 }}
        contextId={contextId}
      />
    </div>
  );
};

export default DynamicAssetField;
