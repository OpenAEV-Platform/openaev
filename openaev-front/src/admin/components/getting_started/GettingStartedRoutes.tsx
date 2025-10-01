import { lazy } from 'react';
import { Route } from 'react-router';

import { errorWrapper } from '../../../components/Error';

const GettingStarted = lazy(() => import('./GettingStartedPage'));

export const GETTING_STARTED_URI = 'getting_started';

const GettingStartedRoutes = (
  <Route path={GETTING_STARTED_URI} element={errorWrapper(GettingStarted)()} />
);

export default GettingStartedRoutes;
