import { FactCheckOutlined, MailOutlined, NoteAltOutlined, TheatersOutlined } from '@mui/icons-material';
import { Drawer, ListItemIcon, ListItemText, MenuItem, MenuList } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link, useLocation } from 'react-router';
import { type CSSObject } from 'tss-react';
import { makeStyles } from 'tss-react/mui';

import type { LoggedHelper } from '../../../../actions/helper';
import { useFormatter } from '../../../../components/i18n';
import { computeBannerSettings } from '../../../../public/components/systembanners/utils';
import { useHelper } from '../../../../store';
import { type Exercise } from '../../../../utils/api-types';

const useStyles = makeStyles()(theme => ({
  drawer: {
    minHeight: '100vh',
    width: 200,
    position: 'fixed',
    overflow: 'auto',
    padding: 0,
  },
  toolbar: theme.mixins.toolbar as CSSObject,
  item: {
    paddingTop: 10,
    paddingBottom: 10,
  },
}));

interface Props { exerciseId: Exercise['exercise_id'] }

const AnimationMenu: FunctionComponent<Props> = ({ exerciseId }) => {
  const location = useLocation();
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { settings } = useHelper((helper: LoggedHelper) => {
    return { settings: helper.getPlatformSettings() };
  });
  const { bannerHeight } = computeBannerSettings(settings);

  return (
    <Drawer
      variant="permanent"
      anchor="right"
      classes={{ paper: classes.drawer }}
    >
      <div
        className={classes.toolbar}
        style={{ marginTop: bannerHeight }}
      />
      <MenuList component="nav">
        <MenuItem
          component={Link}
          to={`/admin/simulations/${exerciseId}/animation/timeline`}
          selected={
            location.pathname
            === `/admin/simulations/${exerciseId}/animation/timeline`
          }
          classes={{ root: classes.item }}
        >
          <ListItemIcon>
            <TheatersOutlined />
          </ListItemIcon>
          <ListItemText primary={t('Timeline')} />
        </MenuItem>
        <MenuItem
          component={Link}
          to={`/admin/simulations/${exerciseId}/animation/mails`}
          selected={location.pathname.includes(
            `/admin/simulations/${exerciseId}/animation/mails`,
          )}
          classes={{ root: classes.item }}
        >
          <ListItemIcon>
            <MailOutlined />
          </ListItemIcon>
          <ListItemText primary={t('Mails')} />
        </MenuItem>
        <MenuItem
          component={Link}
          to={`/admin/simulations/${exerciseId}/animation/validations`}
          selected={
            location.pathname
            === `/admin/simulations/${exerciseId}/animation/validations`
          }
          classes={{ root: classes.item }}
        >
          <ListItemIcon>
            <FactCheckOutlined />
          </ListItemIcon>
          <ListItemText primary={t('Validations')} />
        </MenuItem>
        <MenuItem
          component={Link}
          to={`/admin/simulations/${exerciseId}/animation/logs`}
          selected={
            location.pathname
            === `/admin/simulations/${exerciseId}/animation/logs`
          }
          classes={{ root: classes.item }}
        >
          <ListItemIcon>
            <NoteAltOutlined />
          </ListItemIcon>
          <ListItemText primary={t('Simulation logs')} />
        </MenuItem>
      </MenuList>
    </Drawer>
  );
};

export default AnimationMenu;
