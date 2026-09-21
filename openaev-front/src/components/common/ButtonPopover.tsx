import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { MoreVert } from '@mui/icons-material';
import { Divider, ListItemIcon, ListItemText, Menu, MenuItem } from '@mui/material';
import { type CSSProperties, type Dispatch, type FunctionComponent, type ReactNode, type SetStateAction, useState } from 'react';

import { useFormatter } from '../i18n';

export interface PopoverEntry {
  label: string;
  action: () => void | Dispatch<SetStateAction<boolean>>;
  disabled?: boolean;
  disabledMessage?: string;
  userRight: boolean;
  /** Optional leading icon rendered before the label (for grouped action menus). */
  icon?: ReactNode;
  /** Draw a separator above this entry (ignored when it is the first visible entry). */
  dividerBefore?: boolean;
}

export type VariantButtonPopover = 'toggle' | 'icon';

interface Props {
  entries: PopoverEntry[];
  style?: CSSProperties;
  /** Placement: `icon` in a list row (24px kebab), `toggle` in a detail header (36px kebab, the height of the header controls). */
  variant?: VariantButtonPopover;
  disabled?: boolean;
  className?: string;
  /** @deprecated kept for API compatibility; the icon kebab is always compact now. */
  size?: 'small' | 'medium' | 'large';
}

const ButtonPopover: FunctionComponent<Props> = ({
  entries,
  style,
  variant = 'icon',
  disabled = false,
  className,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const [anchorEl, setAnchorEl] = useState<Element | null>(null);

  const visibleEntries = entries.filter(entry => entry.userRight);
  const allDisabled = disabled || visibleEntries.every(entry => entry.disabled);

  return (
    <>
      {/* The ONE kebab trigger, aligned with OpenCTI and identical everywhere
          (list rows, detail heroes, drawers): a small squared (4px radius)
          transparent primary button - never a large round IconButton, never a
          bordered ToggleButton. */}
      {visibleEntries.length > 0
        && (
          <IconButton
            icon={<MoreVert fontSize="small" />}
            className={className}
            value="popover"
            aria-label={t('More actions')}
            onClick={(ev) => {
              // The kebab may live inside a real link (card / row wrapped in a
              // router <Link> for ctrl+click support): stopPropagation() alone
              // does not cancel the browser's native anchor navigation, so
              // preventDefault() is mandatory here.
              ev.preventDefault();
              ev.stopPropagation();
              setAnchorEl(ev.currentTarget);
            }}
            style={{ ...style }}
            disabled={allDisabled}
            priority="tertiary"
            size={variant === 'toggle' ? 'md' : 'sm'}
          />
        )}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
      >
        {entries.filter(entry => entry.userRight).map((entry, index) => {
          const menuItem = (
            <MenuItem
              key={entry.label}
              disabled={entry.disabled}
              onClick={() => {
                entry.action();
                setAnchorEl(null);
              }}
            >
              {entry.icon && <ListItemIcon>{entry.icon}</ListItemIcon>}
              <ListItemText>{t(entry.label)}</ListItemText>
            </MenuItem>
          );
          const item = (entry.disabled && entry.disabledMessage)
            ? (
                <Tooltip key={entry.label}>
                  <TooltipTrigger asChild>
                    <span>{menuItem}</span>
                  </TooltipTrigger>
                  <TooltipContent>{t(entry.disabledMessage)}</TooltipContent>
                </Tooltip>
              )
            : menuItem;
          // A separator only makes sense between entries, never at the very top.
          if (entry.dividerBefore && index > 0) {
            return [<Divider key={`${entry.label}-divider`} component="li" />, item];
          }
          return item;
        })}
      </Menu>
    </>
  );
};

export default ButtonPopover;
