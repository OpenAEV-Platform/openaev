import { CssBaseline } from '@mui/material';
import { StyledEngineProvider } from '@mui/material/styles';

import ConnectedIntlProvider from '../components/AppIntlProvider';
import ConnectedThemeProvider from '../components/AppThemeProvider';
import Index from './Index';

const Root = () => (
  <StyledEngineProvider injectFirst={true}>
    <ConnectedIntlProvider>
      <ConnectedThemeProvider>
        <CssBaseline />
        <Index />
      </ConnectedThemeProvider>
    </ConnectedIntlProvider>
  </StyledEngineProvider>
);

export default Root;
