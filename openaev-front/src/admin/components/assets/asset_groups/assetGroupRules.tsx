import { DevicesOtherOutlined } from '@mui/icons-material';
import { Box, Chip } from '@mui/material';
import { Fragment } from 'react';

import ClickableModeChip from '../../../../components/common/chips/ClickableModeChip';
import FilterChipValues from '../../../../components/common/queryable/filter/FilterChipValues';
import { type Translate } from '../../../../components/i18n';
import { type AssetGroup, type AssetGroupOutput } from '../../../../utils/api-types';

const CHIP_SX = {
  borderRadius: 1,
  height: 20,
};

// Shared rule rendering (dynamic filter chips + static managed assets count)
// used by both the asset groups list and the asset group overview page.
const computeRuleValues = (
  assetGroup: AssetGroup | AssetGroupOutput,
  t: Translate,
) => {
  const dynamicFilters = assetGroup.asset_group_dynamic_filter?.filters ?? [];
  const hasDynamic = dynamicFilters.length > 0;
  const staticCount = assetGroup.asset_group_assets?.length ?? 0;
  const hasStatic = staticCount > 0;

  // No rule at all: a single placeholder, not a stray "-" before nothing.
  if (!hasDynamic && !hasStatic) {
    return <>-</>;
  }

  return (
    <Box
      sx={{
        padding: '0px 4px',
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 1,
      }}
    >
      {dynamicFilters.map((filter, idx) => (
        <Fragment key={filter.key}>
          {idx !== 0 && <ClickableModeChip mode={assetGroup.asset_group_dynamic_filter?.mode} />}
          <Chip
            variant="filled"
            size="small"
            sx={CHIP_SX}
            label={<FilterChipValues filter={filter} />}
          />
        </Fragment>
      ))}
      {hasDynamic && hasStatic && <span>{t('and')}</span>}
      {hasStatic && (
        <Chip
          variant="filled"
          size="small"
          sx={CHIP_SX}
          icon={<DevicesOtherOutlined sx={{ fontSize: 14 }} />}
          label={t('{count} managed assets', { count: staticCount })}
        />
      )}
    </Box>
  );
};

export default computeRuleValues;
