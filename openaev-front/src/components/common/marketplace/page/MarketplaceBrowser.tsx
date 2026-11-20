import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props {
  hero?: ReactNode;
  // Below the hero, as on the Integrations page: the hero stays put when the tab changes.
  tabs?: ReactNode;
  sidebar: ReactNode;
  toolbar: ReactNode;
  children: ReactNode;
  mainBoxStyle?: React.CSSProperties;
  childrenBoxStyle?: React.CSSProperties;
}

// Marketplace page body: optional hero, then facets on the left and toolbar + results on the right.
const MarketplaceBrowser: FunctionComponent<Props> = ({ hero, tabs, sidebar, toolbar, children, mainBoxStyle, childrenBoxStyle }) => {
  const theme = useTheme();
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(3),
      ...mainBoxStyle
    }}
    >
      {hero}
      {tabs}
      <div style={{
        display: 'flex',
        gap: theme.spacing(3),
        alignItems: 'flex-start',
      }}
      >
        {sidebar}
        <div style={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
          ...childrenBoxStyle
        }}
        >
          {/* The column gap is the only space under the toolbar, as on Integrations: child selectors need sx. */}
          <Box sx={{
            '& > div:first-of-type': { marginTop: 0 },
            '& > div:last-of-type': { marginBottom: 0 },
          }}
          >
            {toolbar}
          </Box>
          {children}
        </div>
      </div>
    </div>
  );
};

export default MarketplaceBrowser;
