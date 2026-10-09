import { Box, Skeleton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props {
  loading: boolean;
  isEmpty: boolean;
  emptyState: ReactNode;
  children: ReactNode;
}

const GRID_SX = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
  gap: 2,
};

// Card grid of a marketplace, with a skeleton shaped like MarketplaceCard while loading.
const MarketplaceGrid: FunctionComponent<Props> = ({ loading, isEmpty, emptyState, children }) => {
  const theme = useTheme();
  if (loading) {
    return (
      <Box sx={GRID_SX}>
        {Array.from({ length: 12 }).map((_, idx) => (
          <Box
            key={idx}
            sx={{
              height: 200,
              borderRadius: 1,
              overflow: 'hidden',
              border: `1px solid ${theme.palette.divider}`,
            }}
          >
            <Skeleton variant="rectangular" height={64} animation="wave" />
            <Box sx={{ padding: 2 }}>
              <Skeleton variant="text" width="80%" height={28} animation="wave" />
              <Skeleton variant="text" width="60%" height={20} animation="wave" />
              <Box sx={{
                display: 'flex',
                gap: 1,
                marginTop: 2,
              }}
              >
                <Skeleton variant="circular" width={20} height={20} animation="wave" />
                <Skeleton variant="circular" width={20} height={20} animation="wave" />
                <Skeleton variant="rectangular" width={60} height={20} animation="wave" sx={{ borderRadius: 1 }} />
              </Box>
            </Box>
          </Box>
        ))}
      </Box>
    );
  }
  if (isEmpty) {
    return <>{emptyState}</>;
  }
  return <Box sx={GRID_SX}>{children}</Box>;
};

export default MarketplaceGrid;
