import { useCallback, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router';

/**
 * URL-routed variant of useTabs: the active tab lives in the URL as a trailing
 * segment (the default tab is the bare detail URL, e.g. /admin/assets/:id, and
 * others append their key, e.g. /admin/assets/:id/statistics), matching the
 * routed tabs of atomic testings and scenarios. Requires the page's route to be
 * declared with a trailing wildcard (".../*").
 */
const useRoutedTabs = (tabKeys: string[], defaultTab: string) => {
  const location = useLocation();
  const navigate = useNavigate();

  const { currentTab, basePath } = useMemo(() => {
    const pathname = location.pathname.replace(/\/+$/, '');
    const lastSegment = pathname.substring(pathname.lastIndexOf('/') + 1);
    if (tabKeys.includes(lastSegment)) {
      return {
        currentTab: lastSegment,
        basePath: pathname.substring(0, pathname.lastIndexOf('/')),
      };
    }
    return {
      currentTab: defaultTab,
      basePath: pathname,
    };
  }, [location.pathname, tabKeys, defaultTab]);

  const handleChangeTab = useCallback((newTab: string) => {
    navigate(newTab === defaultTab ? basePath : `${basePath}/${newTab}`);
  }, [navigate, basePath, defaultTab]);

  return {
    currentTab,
    handleChangeTab,
  };
};

export default useRoutedTabs;
