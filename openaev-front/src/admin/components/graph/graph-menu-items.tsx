import { MenuItem } from '@filigran/design-system';
import { type ReactNode } from 'react';
import { Link } from 'react-router';

// The item builders of the graph's right-click menu (GraphContextMenu); the explorer composes them
// into the sections that apply to what is under the pointer.

// Every item keeps the icon slot, an empty one when it has no icon, so all labels of a menu share
// one left edge.
const iconSlot = (icon: ReactNode) => icon ?? (
  <span
    aria-hidden
    style={{
      display: 'inline-block',
      width: 20,
      height: 20,
    }}
  />
);

export const menuItem = (key: string, label: string, icon: ReactNode, onSelect: () => void, disabled = false) => (
  <MenuItem key={key} startIcon={iconSlot(icon)} disabled={disabled} onSelect={onSelect}>{label}</MenuItem>
);

// A submenu trigger takes no startIcon: its icon sits in its label, with the same slot width.
export const subTriggerLabel = (icon: ReactNode, label: string) => (
  <span className="flex items-center gap-2">
    {icon}
    {label}
  </span>
);

export const linkItem = (key: string, label: string, icon: ReactNode, href: string | undefined) => {
  if (!href) {
    return [];
  }
  return [(
    <MenuItem key={key} startIcon={icon} asChild>
      <a href={href} target="_blank" rel="noopener noreferrer">{label}</a>
    </MenuItem>
  )];
};

// An entity of this product: a router link, so a middle click or "open in new tab" works as on any other entity link.
export const routeItem = (key: string, label: string, icon: ReactNode, href: string | undefined) => {
  if (!href) {
    return [];
  }
  return [(
    <MenuItem key={key} startIcon={icon} asChild>
      <Link to={href}>{label}</Link>
    </MenuItem>
  )];
};
