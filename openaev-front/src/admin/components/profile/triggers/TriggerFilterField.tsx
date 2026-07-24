import { InfoOutlined } from '@mui/icons-material';
import { InputLabel, Tooltip } from '@mui/material';
import { type FunctionComponent } from 'react';

import FilterField from '../../../../components/common/queryable/filter/FilterField';
import { emptyFilterGroup } from '../../../../components/common/queryable/filter/FilterUtils';
import useFiltersState from '../../../../components/common/queryable/filter/useFiltersState';
import { useFormatter } from '../../../../components/i18n';
import { type FilterGroup } from '../../../../utils/api-types';

interface Props {
  entityPrefix: string;
  value?: FilterGroup;
  onChange?: (value: FilterGroup) => void;
}

/**
 * Programmatic relation filters (used by contextual searches) that make no
 * sense as user-facing trigger criteria: no human picks raw entity ids.
 */
const TECHNICAL_FILTER_KEYS = [
  'asset_id',
  'assetGroups',
  'team_injects',
  'asset_group_injects',
  'finding_id',
];

/**
 * Filter group editor bound to the watched resource type of a live trigger.
 * Remount it (with a key) whenever the resource type changes so the available
 * filterable properties are re-resolved.
 */
const TriggerFilterField: FunctionComponent<Props> = ({
  entityPrefix,
  value,
  onChange,
}) => {
  const { t } = useFormatter();
  const [filterGroup, helpers] = useFiltersState(value ?? emptyFilterGroup, undefined, onChange);

  return (
    <div style={{ marginTop: 20 }}>
      <div style={{
        display: 'flex',
        alignItems: 'end',
        gap: 10,
      }}
      >
        <InputLabel id="trigger-filters">{t('Filters')}</InputLabel>
        <Tooltip title={t('Only events on entities matching these filters will notify you. Leave empty to match all entities of the selected type.')}>
          <InfoOutlined
            fontSize="small"
            color="primary"
            style={{ marginTop: 8 }}
          />
        </Tooltip>
      </div>
      <FilterField
        entityPrefix={entityPrefix}
        availableFilterNames={[]}
        excludedFilterNames={TECHNICAL_FILTER_KEYS}
        filterGroup={filterGroup}
        helpers={helpers}
        style={{ marginTop: 20 }}
      />
    </div>
  );
};

export default TriggerFilterField;
