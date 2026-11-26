import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

const MarketplaceGrid: FunctionComponent<{ children: ReactNode }> = ({ children }) => {
  const theme = useTheme();
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
      gap: theme.spacing(2),
    }}
    >
      {children}
    </div>
  );
};

export default MarketplaceGrid;
