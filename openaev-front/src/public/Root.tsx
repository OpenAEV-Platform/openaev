import { CssBaseline } from '@mui/material';
import { StyledEngineProvider } from '@mui/material/styles';

import { type LoggedHelper } from '../actions/helper';
import ConnectedIntlProvider from '../components/AppIntlProvider';
import ConnectedThemeProvider from '../components/AppThemeProvider';
import { useHelper } from '../store';
import { type PlatformSettings } from '../utils/api-types';
import { defineAbility } from '../utils/permissions/ability';
import { AbilityProvider } from '../utils/permissions/permissionsContext';
import SystemBanners from './components/systembanners/SystemBanners';
import Index from './Index';

// Anonymous visitors have no rights, but shared permission hooks still need a provider.
const noAbility = defineAbility([], {}, false);

const Root = () => {
  const { settings }: { settings: PlatformSettings } = useHelper(
    (helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }),
  );

  return (
    <StyledEngineProvider injectFirst={true}>
      <ConnectedIntlProvider>
        <ConnectedThemeProvider>
          <CssBaseline />
          {/* Keep system banners visible on public/login pages too (safe mode + platform alerts). */}
          <SystemBanners settings={settings} />
          <AbilityProvider value={noAbility}>
            <Index />
          </AbilityProvider>
        </ConnectedThemeProvider>
      </ConnectedIntlProvider>
    </StyledEngineProvider>
  );
};

export default Root;
