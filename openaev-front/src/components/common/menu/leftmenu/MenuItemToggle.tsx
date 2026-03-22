import { ChevronLeft, ChevronRight } from '@mui/icons-material';
import { ListItemIcon, ListItemText, MenuItem } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../i18n';
import useLeftMenuStyle from './useLeftMenuStyle';

interface Props {
  navOpen: boolean;
  onClick: () => void;
}

const MenuItemToggle: FunctionComponent<Props> = ({ navOpen, onClick }) => {
  const { t } = useFormatter();
  const { iconSx, getMenuItemSx, textColor } = useLeftMenuStyle();

  return (
    <MenuItem
      aria-label={navOpen ? 'Collapse menu' : 'Expand menu'}
      dense
      onClick={onClick}
      sx={getMenuItemSx(false)}
    >
      <ListItemIcon sx={iconSx}>
        {navOpen ? <ChevronLeft /> : <ChevronRight />}
      </ListItemIcon>
      {navOpen && (
        <ListItemText
          primary={t('Collapse')}
          sx={{ pt: 0.1 }}
          slotProps={{
            primary: {
              fontSize: '14px',
              color: textColor,
            },
          }}
        />
      )}
    </MenuItem>
  );
};

export default MenuItemToggle;
