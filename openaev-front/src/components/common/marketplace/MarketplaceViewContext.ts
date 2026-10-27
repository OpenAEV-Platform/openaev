import { createContext, useContext } from 'react';

import { type ViewMode } from './useMarketplaceView';

// Set by MarketplaceGrid so each MarketplaceCard renders as a card or as a list row.
export const MarketplaceViewContext = createContext<ViewMode>('grid');

export const useMarketplaceViewMode = () => useContext(MarketplaceViewContext);
