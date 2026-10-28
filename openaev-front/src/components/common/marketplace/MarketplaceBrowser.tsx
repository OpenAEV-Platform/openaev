import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props {
  hero?: ReactNode;
  // Below the hero, as on the Integrations page: the hero stays put when the tab changes.
  tabs?: ReactNode;
  sidebar: ReactNode;
  toolbar: ReactNode;
  children: ReactNode;
}

// Marketplace page body: optional hero, then facets on the left and toolbar + results on the right.
const MarketplaceBrowser: FunctionComponent<Props> = ({ hero, tabs, sidebar, toolbar, children }) => (
  <Box sx={{
    display: 'flex',
    flexDirection: 'column',
    gap: 3,
  }}
  >
    {hero}
    {tabs}
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
        {/* The column gap is the only space under the toolbar, as on Integrations. */}
        <Box sx={{
          '& > div:first-of-type': { marginTop: 0 },
          '& > div:last-of-type': { marginBottom: 0 },
        }}
        >
          {toolbar}
        </Box>
        {children}
      </Box>
    </Box>
  </Box>
);

export default MarketplaceBrowser;
