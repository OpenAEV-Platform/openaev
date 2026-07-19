import { Box, Chip } from '@mui/material';
import { Fragment } from 'react';

import ClickableModeChip from '../../../../components/common/chips/ClickableModeChip';
import FilterChipValues from '../../../../components/common/queryable/filter/FilterChipValues';
import { type AssetGroup, type AssetGroupOutput } from '../../../../utils/api-types';

// Shared rule rendering (dynamic filter chips + static managed assets count)
// used by both the asset groups list and the asset group overview page.
const computeRuleValues = (assetGroup: AssetGroup | AssetGroupOutput, t: (value: string) => string) => {
  const computeDynamic = () => {
    if (assetGroup.asset_group_dynamic_filter?.filters && assetGroup.asset_group_dynamic_filter?.filters.length > 0) {
      return (
        <>
          {assetGroup.asset_group_dynamic_filter.filters.map((filter, idx) => (
            <Fragment key={filter.key}>
              {idx !== 0 && <ClickableModeChip mode={assetGroup.asset_group_dynamic_filter?.mode} />}
              <Chip
                key={filter.key}
                variant="filled"
                size="small"
                sx={{
                  borderRadius: 1,
                  height: 20,
                }}
                label={<FilterChipValues filter={filter} />}
              />
            </Fragment>
          ))}
        </>
      );
    }
    return (<>-</>);
  };

  const computeStatic = () => {
    if (assetGroup.asset_group_assets && assetGroup.asset_group_assets?.length > 0) {
      return (
        <div style={{ alignContent: 'center' }}>
          {assetGroup.asset_group_assets?.length}
          {' '}
          {t('managed assets')}
        </div>
      );
    }
    return null;
  };

  const andWord = () => {
    if (assetGroup.asset_group_dynamic_filter?.filters && assetGroup.asset_group_dynamic_filter?.filters.length > 0
      && assetGroup.asset_group_assets && assetGroup.asset_group_assets?.length > 0) {
      return (<div style={{ alignContent: 'center' }}>{t('and')}</div>);
    }
    return null;
  };

  return (
    <Box
      sx={{
        padding: '0px 4px',
        display: 'flex',
        flexWrap: 'wrap',
        gap: 1,
      }}
    >
      {computeDynamic()}
      {andWord()}
      {computeStatic()}
    </Box>
  );
};

export default computeRuleValues;
