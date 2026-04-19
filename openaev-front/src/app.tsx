import { Provider } from 'react-redux';
import { BrowserRouter, Route, Routes } from 'react-router';

import NotFound from './components/NotFound';
import RedirectManager from './components/RedirectManager';
import Root from './root';
import { store } from './store';
import { computeTenantBasename } from './utils/tenant-url-helper';

// Computed once at page load — tenant UUID is extracted from the URL.
// On tenant switch the page does a full reload, so the basename is recomputed.
const basename = computeTenantBasename();

const App = () => (
  <Provider store={store}>
    <BrowserRouter key={basename} basename={basename}>
      <RedirectManager>
        <Routes>
          <Route path="/*" element={<Root />} />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </RedirectManager>
    </BrowserRouter>
  </Provider>
);

export default App;
