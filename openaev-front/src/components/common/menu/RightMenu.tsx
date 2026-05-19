import { Drawer, ListItemIcon, ListItemText, MenuItem, MenuList } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement } from 'react';
import { Link, useLocation } from 'react-router';
import { type CSSObject } from 'tss-react';
import { makeStyles } from 'tss-react/mui';

import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import useAuth from '../../../utils/hooks/useAuth';
import { isNotEmptyField } from '../../../utils/utils';
import { useFormatter } from '../../i18n';

const useStyles = makeStyles()(theme => ({ toolbar: theme.mixins.toolbar as CSSObject }));

export interface RightMenuEntry {
  path: string;
  icon: () => ReactElement;
  label: string;
  number?: number;
  chip?: ReactElement;
  onClick?: () => void;
}

const RightMenu: FunctionComponent<{ entries: RightMenuEntry[] }> = ({ entries }) => {
  // Standard hooks
  const location = useLocation();
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();

  const { settings } = useAuth();
  const { bannerHeight } = computeBannerSettings(settings);

  return (
    <Drawer
      variant="permanent"
      anchor="right"
      sx={{
        'width': 200,
        '& .MuiDrawer-paper': {
          width: 200,
          backgroundColor: theme.palette.background.nav,
        },
      }}
    >
      <div className={classes.toolbar} />
      <MenuList component="nav" sx={{ marginTop: bannerHeight }}>
        {entries.map((entry, idx) => {
          const isCurrentTab = location.pathname === entry.path;
          return (
            <MenuItem
              key={idx}
              component={Link}
              to={entry.onClick ? '#' : entry.path}
              selected={isCurrentTab}
              onClick={entry.onClick
                ? (e: React.MouseEvent) => {
                    e.preventDefault();
                    entry.onClick?.();
                  }
                : undefined}
              sx={{
                paddingTop: theme.spacing(1),
                paddingBottom: theme.spacing(1),
              }}
            >
              <ListItemIcon>
                {entry.icon()}
              </ListItemIcon>
              <ListItemText primary={isNotEmptyField(entry.number) ? `${t(entry.label)} (${entry.number})` : t(entry.label)} />
              {entry.chip && <>{entry.chip}</>}
            </MenuItem>
          );
        })}
      </MenuList>
    </Drawer>
  );
};

export default RightMenu;
