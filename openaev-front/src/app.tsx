import { Provider } from 'react-redux';
import { BrowserRouter, Route, Routes } from 'react-router';

import NotFound from './components/NotFound';
import RedirectManager from './components/RedirectManager';
import Root from './root';
import FdsLeftBarPreview from './spike/FdsLeftBarPreview';
import FdsNavbarSpike from './spike/FdsNavbarSpike';
import { store } from './store';
import { computeTenantBasename } from './utils/url-helper';

const basename = computeTenantBasename();

const App = () => {
  return (
    <Provider store={store}>
      <BrowserRouter key={basename} basename={basename}>
        <RedirectManager>
          <Routes>
            {/* TEMPORARY DESIGN-SYSTEM SPIKE — standalone, no-login route only used to
                visually check @filigran/design-system's Navbar/ProductSwitcher. Not part
                of real navigation; safe to remove along with src/spike/. */}
            <Route path="/spike/fds-navbar" element={<FdsNavbarSpike />} />
            {/* LOCAL-ONLY DEMO HARNESS — never commit. Real LeftBar.tsx wired to
                the new FDS Navbar, stubbed auth context (see FdsLeftBarPreview.tsx). */}
            <Route path="/spike/fds-leftbar-preview" element={<FdsLeftBarPreview />} />
            <Route path="/*" element={<Root />} />
            {/* Not found */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </RedirectManager>
      </BrowserRouter>
    </Provider>
  );
};

export default App;
