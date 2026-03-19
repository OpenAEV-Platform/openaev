import { MoreVert } from '@mui/icons-material';
import { IconButton, Menu, MenuItem, ToggleButton } from '@mui/material';
import { type CSSProperties, type Dispatch, type FunctionComponent, type SetStateAction, useState } from 'react';

import { useFormatter } from '../i18n';

export interface PopoverEntry {
  label: string;
  action: () => void | Dispatch<SetStateAction<boolean>>;
  disabled?: boolean;
  userRight: boolean;
}

export type VariantButtonPopover = 'toggle' | 'icon';

interface Props {
  entries: PopoverEntry[];
  style?: CSSProperties;
  variant?: VariantButtonPopover;
  disabled?: boolean;
  className?: string;
  size?: 'small' | 'medium' | 'large';
}

const ButtonPopover: FunctionComponent<Props> = ({
  entries,
  style,
  variant = 'toggle',
  disabled = false,
  className,
  size = 'large',
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const [anchorEl, setAnchorEl] = useState<Element | null>(null);

  return (
    <>
      {variant === 'toggle' && !entries.every(entry => !entry.userRight)
        && (
          <ToggleButton
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
          >
            <MoreVert fontSize="small" color={disabled ? 'disabled' : 'primary'} />
          </ToggleButton>
        )}
      {variant === 'icon' && !entries.every(entry => !entry.userRight)
        && (
          <IconButton
            value="popover"
            size={size}
            color="primary"
            onClick={(ev) => {
              ev.stopPropagation();
              setAnchorEl(ev.currentTarget);
            }}
            style={{ ...style }}
            disabled={disabled}
          >
            <MoreVert fontSize={size === 'small' ? 'small' : 'medium'} color={disabled ? 'disabled' : 'primary'} />
          </IconButton>
        )}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
      >
        {entries.filter(entry => entry.userRight).map((entry) => {
          return (
            <MenuItem
              key={entry.label}
              disabled={entry.disabled}
              onClick={() => {
                entry.action();
                setAnchorEl(null);
              }}
            >
              {t(entry.label)}
            </MenuItem>
          );
        })}
      </Menu>
    </>
  );
};

export default ButtonPopover;
