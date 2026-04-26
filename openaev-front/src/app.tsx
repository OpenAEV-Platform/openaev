import { Provider } from 'react-redux';
import { BrowserRouter, Route, Routes } from 'react-router';

import NotFound from './components/NotFound';
import RedirectManager from './components/RedirectManager';
import Root from './root';
import { store } from './store';
import { APP_BASE_PATH } from './utils/Environment';
import { PlatformModeProvider } from './utils/platformContext';
import { computeTenantBasename, isCurrentPlatformRoute } from './utils/url-helper';
import useRouteBoundaryGuard from './utils/useRouteBoundaryGuard';

// Computed once at page load — determines which router to use.
// On context switch (tenant ↔ platform) the page does a full reload,
// so these values are recomputed.
const isPlatform = isCurrentPlatformRoute();
const basename = isPlatform ? (APP_BASE_PATH || '') : computeTenantBasename();

const App = () => {
  useRouteBoundaryGuard(isPlatform);

  return (
    <Provider store={store}>
      <PlatformModeProvider value={isPlatform}>
        <BrowserRouter key={basename} basename={basename}>
          <RedirectManager>
            <Routes>
              <Route path="/*" element={<Root />} />
              {/* Not found */}
              <Route path="*" element={<NotFound />} />
            </Routes>
          </RedirectManager>
        </BrowserRouter>
      </PlatformModeProvider>
    </Provider>
  );
};

export default App;
