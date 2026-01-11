import { type MutableRefObject, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { MESSAGING$ } from '../../../../utils/Environment';
import { hasHref, type LeftMenuEntries } from './leftmenu-model';

export interface LeftMenuState {
  navOpen: boolean;
  selectedMenu: string | null;
  anchors: Record<string, MutableRefObject<HTMLLIElement | null>>;
}

export interface LeftMenuHelpers {
  handleToggleDrawer: () => void;
  handleSelectedMenuOpen: (menu: string) => void;
  handleSelectedMenuClose: () => void;
  handleSelectedMenuToggle: (menu: string) => void;
  handleGoToPage: (path: string) => void;
}

const useLeftMenu = (entries: LeftMenuEntries[]): {
  state: LeftMenuState;
  helpers: LeftMenuHelpers;
} => {
  // Standard hooks
  const navigate = useNavigate();

  const [navOpen, setNavOpen] = useState(localStorage.getItem('navOpen') === 'true');
  const [selectedMenu, setSelectedMenu] = useState<string | null>(null);

  // Store all anchor refs in a single ref object to comply with rules of hooks
  const anchorsRef = useRef<Record<string, MutableRefObject<HTMLLIElement | null>>>({});

  // Compute the list of hrefs that need refs and initialize them only once
  const anchors = useMemo(() => {
    const hrefs = entries.flatMap(entry =>
      entry.items.filter(hasHref).map(item => item.href),
    );
    // Initialize refs for any new hrefs
    hrefs.forEach((href) => {
      if (!anchorsRef.current[href]) {
        anchorsRef.current[href] = { current: null };
      }
    });
    return anchorsRef.current;
  }, [entries]);

  const handleToggleDrawer = () => {
    setSelectedMenu(null);
    localStorage.setItem('navOpen', String(!navOpen));
    setNavOpen(!navOpen);
    MESSAGING$.toggleNav.next();
  };

  const handleSelectedMenuOpen = (menu: string) => setSelectedMenu(menu);
  const handleSelectedMenuClose = () => setSelectedMenu(null);
  const handleSelectedMenuToggle = (menu: string) => setSelectedMenu(selectedMenu === menu ? null : menu);

  const handleGoToPage = (path: string) => {
    navigate(path);
    setSelectedMenu(null);
  };

  return {
    state: {
      navOpen,
      selectedMenu,
      anchors,
    },
    helpers: {
      handleToggleDrawer,
      handleSelectedMenuOpen,
      handleSelectedMenuClose,
      handleSelectedMenuToggle,
      handleGoToPage,
    },
  };
};

export default useLeftMenu;
