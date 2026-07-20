import { MoreVert } from '@mui/icons-material';
import { Divider, IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Tooltip } from '@mui/material';
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
  /** @deprecated kept for API compatibility; every kebab renders the same OpenCTI-style compact squared button now. */
  variant?: VariantButtonPopover;
  disabled?: boolean;
  className?: string;
  /** @deprecated kept for API compatibility; the icon kebab is always compact now. */
  size?: 'small' | 'medium' | 'large';
}

const ButtonPopover: FunctionComponent<Props> = ({
  entries,
  style,
  disabled = false,
  className,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const [anchorEl, setAnchorEl] = useState<Element | null>(null);

  return (
    <>
      {/* The ONE kebab trigger, aligned with OpenCTI and identical everywhere
          (list rows, detail heroes, drawers): a small squared (4px radius)
          transparent primary button - never a large round IconButton, never a
          bordered ToggleButton. */}
      {!entries.every(entry => !entry.userRight)
        && (
          <IconButton
            className={className}
            value="popover"
            size="small"
            color="primary"
            onClick={(ev) => {
              ev.stopPropagation();
              setAnchorEl(ev.currentTarget);
            }}
            style={{ ...style }}
            disabled={disabled}
            sx={{ borderRadius: 1 }}
          >
            <MoreVert fontSize="small" color={disabled ? 'disabled' : 'primary'} />
          </IconButton>
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
                <Tooltip key={entry.label} title={t(entry.disabledMessage)}>
                  <span>{menuItem}</span>
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
