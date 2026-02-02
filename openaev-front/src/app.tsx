import { Provider } from 'react-redux';
import { BrowserRouter, Route, Routes } from 'react-router';

import NotFound from './components/NotFound';
import RedirectManager from './components/RedirectManager';
import Root from './root';
import { store } from './store';
import { APP_BASE_PATH } from './utils/Environment';

const App = () => (
  <Provider store={store}>
    <BrowserRouter basename={APP_BASE_PATH}>
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
