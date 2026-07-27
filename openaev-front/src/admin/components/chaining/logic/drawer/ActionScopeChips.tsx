import { InfoOutlined } from '@mui/icons-material';
import { Box, Chip, Tooltip, Typography } from '@mui/material';

import { useFormatter } from '../../../../../components/i18n';
import type { ScopeAssetOutput } from '../../../../../utils/api-types';

interface ActionScopeChipsProps {
  isPayload: boolean;
  validAssets: ScopeAssetOutput[];
}

const ActionScopeChips = ({ isPayload, validAssets }: ActionScopeChipsProps) => {
  const { t } = useFormatter();

  const title = isPayload ? t('Initial Source Assets') : t('Initial Target');
  const tooltip = isPayload
    ? t('Additional endpoints may be included during simulation based on real decision logic.')
    : t('Additional targets may be included during simulation based on real decision logic.');
  const emptyLabel = isPayload ? t('No assets in the allow list.') : t('No targets in the allow list.');

  return (
    <Box>
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        mb: 1,
      }}
      >
        <Typography variant="subtitle2" fontWeight={600}>{title}</Typography>
        <Tooltip title={tooltip}>
          <InfoOutlined fontSize="small" color="info" />
        </Tooltip>
      </Box>
      {validAssets.length > 0 ? (
        <Box sx={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 0.5,
        }}
        >
          {validAssets.map(asset => (
            <Chip
              key={asset.asset_id ?? ''}
              label={asset.asset_name ?? ''}
              size="small"
              variant="filled"
            />
          ))}
        </Box>
      ) : (
        <Typography variant="body2" color="text.secondary">{emptyLabel}</Typography>
      )}
    </Box>
  );
};

export default ActionScopeChips;
