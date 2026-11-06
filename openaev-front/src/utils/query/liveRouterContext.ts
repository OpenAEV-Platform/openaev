import { createContext, useContext } from 'react';

import { type LiveRouter } from './liveRouter';

export const LiveRouterContext = createContext<LiveRouter | null>(null);

export const useLiveRouter = (): LiveRouter => {
  const router = useContext(LiveRouterContext);
  if (!router) throw new Error('useLiveRouter must be used inside AppQueryProvider');
  return router;
};
