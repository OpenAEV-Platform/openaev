import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

const MarketplaceGrid: FunctionComponent<{ children: ReactNode }> = ({ children }) => (
  <Box sx={{
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
    gap: 2,
  }}
  >
    {children}
  </Box>
);

export default MarketplaceGrid;
