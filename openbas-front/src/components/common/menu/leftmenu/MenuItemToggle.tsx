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
  // Standard hooks
  const { t } = useFormatter();
  const leftMenuStyle = useLeftMenuStyle();

  return (
    <MenuItem
      aria-label={navOpen ? 'Collapse menu' : 'Expand menu'}
      dense
      onClick={onClick}
    >
      <ListItemIcon style={{ ...leftMenuStyle.listItemIcon }}>
        {navOpen ? <ChevronLeft /> : <ChevronRight />}
      </ListItemIcon>
      {navOpen && (
        <ListItemText
          primary={t('Collapse')}
          slotProps={{ primary: { sx: { ...leftMenuStyle.listItemText } } }}
        />
      )}
    </MenuItem>
  );
};

export default MenuItemToggle;
