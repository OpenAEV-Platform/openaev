import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props {
  hero?: ReactNode;
  sidebar: ReactNode;
  toolbar: ReactNode;
  children: ReactNode;
}

// Marketplace page body: optional hero, then facets on the left and toolbar + results on the right.
const MarketplaceBrowser: FunctionComponent<Props> = ({ hero, sidebar, toolbar, children }) => (
  <Box sx={{
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
  }}
  >
    {hero}
    <Box sx={{
      display: 'flex',
      gap: 3,
      alignItems: 'flex-start',
    }}
    >
      {sidebar}
      <Box sx={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        <Box sx={{ '& > div:first-of-type': { marginTop: 0 } }}>
          {toolbar}
        </Box>
        {children}
      </Box>
    </Box>
  </Box>
);

export default MarketplaceBrowser;
