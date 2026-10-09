import { useState } from 'react';

export type ViewMode = 'grid' | 'list';

const VIEW_MODE_STORAGE_KEY = 'threat-arsenal:view-mode-v2';

const readViewMode = (): ViewMode => {
  if (typeof window === 'undefined') return 'grid';
  const stored = window.localStorage.getItem(VIEW_MODE_STORAGE_KEY);
  return stored === 'list' ? 'list' : 'grid';
};

const useThreatArsenalView = () => {
  const [viewMode, setViewMode] = useState<ViewMode>(readViewMode);

  const handleViewModeChange = (next: string) => {
    const value = next as ViewMode;
    setViewMode(value);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, value);
    }
  };

  return {
    viewMode,
    handleViewModeChange,
  };
};

export default useThreatArsenalView;
