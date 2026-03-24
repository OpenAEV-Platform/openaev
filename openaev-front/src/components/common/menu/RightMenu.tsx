import { alpha, Drawer, ListItemIcon, ListItemText, MenuItem, MenuList } from '@mui/material';
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
}

const RightMenu: FunctionComponent<{ entries: RightMenuEntry[] }> = ({ entries }) => {
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
        'zIndex': theme.zIndex.drawer - 2,
        'width': 200,
        '& .MuiDrawer-paper': {
          width: 200,
          background: theme.palette.background.nav,
          borderLeft: '1px solid transparent',
          right: 'var(--chatbot-sidebar-width, 0px)',
          transition: 'right 225ms cubic-bezier(0.4, 0, 0.2, 1)',
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
              to={entry.path}
              selected={false}
              dense
              sx={{
                'px': 2,
                'pr': 1,
                'py': 0,
                'height': '36px',
                'borderLeft': isCurrentTab
                  ? `2px solid ${theme.palette.primary.main}`
                  : '2px solid transparent',
                'backgroundColor': isCurrentTab
                  ? alpha(theme.palette.primary.main, 0.1)
                  : 'transparent',
                'display': 'flex',
                'alignItems': 'center',
                '&:hover': {
                  backgroundColor: isCurrentTab
                    ? theme.palette.action?.selected
                    : theme.palette.leftBar.hover,
                },
              }}
            >
              <ListItemIcon
                sx={{
                  'minWidth': '0px !important',
                  'mr': 1,
                  'opacity': 0.5,
                  'color': isCurrentTab
                    ? theme.palette.text.light
                    : theme.palette.text.tertiary,
                  '& svg': { fontSize: '16px !important' },
                }}
              >
                {entry.icon()}
              </ListItemIcon>
              <ListItemText
                primary={isNotEmptyField(entry.number) ? `${t(entry.label)} (${entry.number})` : t(entry.label)}
                sx={{ pt: 0.1 }}
                slotProps={{
                  primary: {
                    fontSize: '14px',
                    color: theme.palette.leftBar.text,
                  },
                }}
              />
            </MenuItem>
          );
        })}
      </MenuList>
    </Drawer>
  );
};

export default RightMenu;
