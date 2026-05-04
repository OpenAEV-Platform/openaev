import { Provider } from 'react-redux';
import { BrowserRouter, Route, Routes } from 'react-router';

import NotFound from './components/NotFound';
import RedirectManager from './components/RedirectManager';
import Root from './root';
import { store } from './store';
import { computeTenantBasename } from './utils/url-helper';

const basename = computeTenantBasename();

const App = () => {
  return (
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
};

export default App;
