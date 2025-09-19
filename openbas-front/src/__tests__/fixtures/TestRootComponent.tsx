import { CssBaseline } from '@mui/material';
import { type ReactNode } from 'react';
import { Provider } from 'react-redux';

import ConnectedIntlProvider from '../../components/AppIntlProvider';
import ConnectedThemeProvider from '../../components/AppThemeProvider';
import { store } from '../../store';

interface Props { children: ReactNode }
const TestRootComponent = ({ children }: Props) => {
  return (
    <Provider store={store}>
      <ConnectedIntlProvider>
        <ConnectedThemeProvider>
          <CssBaseline />
          {children}
        </ConnectedThemeProvider>
      </ConnectedIntlProvider>
    </Provider>
  );
};

export default TestRootComponent;
