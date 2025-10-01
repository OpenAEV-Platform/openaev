import { type MutableRefObject, useRef, useState } from 'react';
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

  const anchors = entries.reduce(
    (acc, entry) =>
      entry.items.reduce((subAcc, item) => {
        if (hasHref(item)) {
          subAcc[item.href] = useRef<HTMLLIElement | null>(null);
        }
        return subAcc;
      }, acc),
    {} as Record<string, MutableRefObject<HTMLLIElement | null>>,
  );

  const handleToggleDrawer = () => {
    setSelectedMenu(null);
    localStorage.setItem('navOpen', String(!navOpen));
    setNavOpen(!navOpen);
    MESSAGING$.toggleNav.next('toggle');
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
