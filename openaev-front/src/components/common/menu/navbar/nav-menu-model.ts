import { type ReactElement } from 'react';

export interface NavMenuEntries {
  items: NavMenuItem[];
  userRight: boolean;
}

export interface NavMenuItem {
  path: string;
  icon: () => ReactElement;
  label: string;
  /** Stable key identifying a group row; its presence marks the item as a group. */
  href?: string;
  subItems?: NavMenuSubItem[];
  userRight?: boolean;
  /**
   * Other route prefixes this row stands for, so it stays current on them
   * (one entry whose tabs are separate routes, e.g. Dashboards | Reporting).
   */
  matchPaths?: string[];
}

export interface NavMenuItemWithHref extends NavMenuItem { href: string }

export function hasHref(item: NavMenuItem): item is NavMenuItemWithHref {
  return typeof item.href === 'string';
}

export interface NavMenuSubItem {
  exact?: boolean;
  link: string;
  label: string;
  icon?: () => ReactElement;
  userRight?: boolean;
}
