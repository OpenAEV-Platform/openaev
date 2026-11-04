import { useState } from 'react';

export type ViewMode = 'grid' | 'list';

const readViewMode = (storageKey: string): ViewMode => {
  if (typeof window === 'undefined') return 'grid';
  const stored = window.localStorage.getItem(storageKey);
  return stored === 'list' ? 'list' : 'grid';
};

// Grid or list view of a marketplace page, remembered per page.
const useMarketplaceView = (storageKey: string) => {
  const [viewMode, setViewMode] = useState<ViewMode>(() => readViewMode(storageKey));

  const handleViewModeChange = (next: string) => {
    const value = next as ViewMode;
    setViewMode(value);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(storageKey, value);
    }
  };

  return {
    viewMode,
    handleViewModeChange,
  };
};

export default useMarketplaceView;
