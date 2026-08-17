import { WarningAmberOutlined } from '@mui/icons-material';
import { Box, Paper, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import type { UpdatedAsset } from './useSnapshotUpdated';

interface ChainingUpdatedBannerProps {
  /** Drifted allow / deny entries, already resolved to display sentences by {@link useSnapshotUpdated}. */
  updatedAssets: UpdatedAsset[];
}

/**
 * Read-only advisory banner shown when at least one allow / deny entry of a launched simulation's
 * chained scope drifted from its launch snapshot.
 *
 * Purely presentational: it receives the already-resolved drifted assets from its parent (via the
 * {@link useSnapshotUpdated} hook) and does no data fetching itself. Renders nothing when there is
 * no drift, so it is safe to drop unconditionally.
 */
const ChainingUpdatedBanner = ({ updatedAssets }: ChainingUpdatedBannerProps) => {
  const theme = useTheme();

  if (updatedAssets.length === 0) {
    return null;
  }

  return (
    <Paper
      variant="outlined"
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        flexWrap: 'wrap',
        padding: 2,
        borderRadius: 1,
        border: `1px solid ${alpha(theme.palette.warning.main, 0.3)}`,
        background: `linear-gradient(135deg, ${alpha(theme.palette.warning.main, 0.1)}, transparent 60%)`,
      }}
    >
      <WarningAmberOutlined sx={{ color: theme.palette.warning.main }} />
      <Box sx={{
        flex: 1,
        minWidth: 240,
      }}
      >
        {updatedAssets.map(asset => (
          <Typography key={asset.id} variant="body2">
            {asset.message}
          </Typography>
        ))}
      </Box>
    </Paper>
  );
};

export default ChainingUpdatedBanner;
