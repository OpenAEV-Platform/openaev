import { Box, Skeleton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { MarketplaceListHeader, MarketplaceListRowSkeleton } from './MarketplaceListRow';
import { MarketplaceViewContext } from './MarketplaceViewContext';
import { type ViewMode } from './useMarketplaceView';

interface Props {
  loading: boolean;
  isEmpty: boolean;
  emptyState: ReactNode;
  children: ReactNode;
  view?: ViewMode;
  // Header of the column showing the card overline, in list view.
  overlineLabel?: string;
}

const GRID_SX = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
  gap: 2,
};

// Card grid of a marketplace, with a skeleton shaped like MarketplaceCard while loading.
const MarketplaceGrid: FunctionComponent<Props> = ({ loading, isEmpty, emptyState, children, view = 'grid', overlineLabel = 'Category' }) => {
  const theme = useTheme();
  if (view === 'list' && (loading || !isEmpty)) {
    return (
      <Box sx={{
        'borderRadius': 1,
        'border': `1px solid ${theme.palette.divider}`,
        'backgroundColor': 'var(--bg-elevation-default-layer-1)',
        'overflow': 'hidden',
        '& > * + *': { borderTop: `1px solid ${theme.palette.divider}` },
      }}
      >
        <MarketplaceListHeader overlineLabel={overlineLabel} />
        {loading
          ? Array.from({ length: 10 }).map((_, idx) => <MarketplaceListRowSkeleton key={idx} />)
          : <MarketplaceViewContext.Provider value="list">{children}</MarketplaceViewContext.Provider>}
      </Box>
    );
  }
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
