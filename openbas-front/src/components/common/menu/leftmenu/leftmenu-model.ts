import { type ReactElement } from 'react';

export interface LeftMenuEntries {
  items: LeftMenuItem[];
  userRight: boolean;
}

export interface LeftMenuItem {
  path: string;
  icon: () => ReactElement;
  label: string;
  href?: string;
  subItems?: LeftMenuSubItem[];
  userRight?: boolean;
}

export interface LeftMenuItemWithHref extends LeftMenuItem { href: string }

export function hasHref(item: LeftMenuItem): item is LeftMenuItemWithHref {
  return typeof item.href === 'string';
}

export interface LeftMenuSubItem {
  exact?: boolean;
  link: string;
  label: string;
  icon?: () => ReactElement;
  userRight?: boolean;
}
