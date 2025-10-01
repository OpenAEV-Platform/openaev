import { InfoOutlined } from '@mui/icons-material';
import { InputLabel, Tooltip } from '@mui/material';
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

  const availableFilterNames = [
    'endpoint_arch',
    'endpoint_hostname',
    'endpoint_ips',
    'endpoint_platform',
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
        <Tooltip title={t('Filter allowing assets to be added dynamically to this group')}>
          <InfoOutlined
            fontSize="small"
            color="primary"
            style={{ marginTop: 8 }}
          />
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
