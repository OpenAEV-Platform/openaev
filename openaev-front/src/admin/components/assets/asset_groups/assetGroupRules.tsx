import { DevicesOtherOutlined } from '@mui/icons-material';
// fds:keep-mui deferred to the filter bar wave (LIBRARY-FEEDBACK #54: the filter chip edits its and/or from the label)
import { Chip } from '@mui/material';

import ClickableModeChip from '../../../../components/common/chips/ClickableModeChip';
import FilterChipValues from '../../../../components/common/queryable/filter/FilterChipValues';
import { type Translate } from '../../../../components/i18n';
import { type AssetGroup, type AssetGroupOutput } from '../../../../utils/api-types';
import AssetGroupRulesCell from './AssetGroupRulesCell';

// Every chip of the Rules column is the same height as the library's own.
const RULES_CHIP_HEIGHT = 24;

const CHIP_SX = {
  borderRadius: 1,
  height: RULES_CHIP_HEIGHT,
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

  // One node per rule, each carrying its own leading separator, so the cell can
  // drop whole rules from the end without ever cutting one in half.
  const items = [
    ...dynamicFilters.map((filter, idx) => (
      <span
        key={filter.key}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 8,
          flexShrink: 0,
        }}
      >
        {idx !== 0 && <ClickableModeChip mode={assetGroup.asset_group_dynamic_filter?.mode} height={RULES_CHIP_HEIGHT} />}
        <Chip
          variant="filled"
          size="small"
          sx={CHIP_SX}
          label={<FilterChipValues filter={filter} />}
        />
      </span>
    )),
    ...(hasStatic
      ? [(
          <span
            key="managed-assets"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
              flexShrink: 0,
            }}
          >
            {hasDynamic && <span>{t('and')}</span>}
            <Chip
              variant="filled"
              size="small"
              sx={CHIP_SX}
              icon={<DevicesOtherOutlined sx={{ fontSize: 14 }} />}
              label={t('{count} managed assets', { count: staticCount })}
            />
          </span>
        )]
      : []),
  ];

  const labels = [
    ...dynamicFilters.map(filter => filter.key),
    ...(hasStatic ? [String(t('{count} managed assets', { count: staticCount }))] : []),
  ];

  return <AssetGroupRulesCell items={items} labels={labels} />;
};

export default computeRuleValues;
