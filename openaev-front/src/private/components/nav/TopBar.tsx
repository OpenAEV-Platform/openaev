import { AccountCircleOutlined } from '@mui/icons-material';
import { AppBar, IconButton, Menu, MenuItem, type MenuProps, Toolbar } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { logout } from '../../../actions/Application';
import { useFormatter } from '../../../components/i18n';
import { useAppDispatch } from '../../../utils/hooks';

const useStyles = makeStyles()(theme => ({
  appBar: {
    width: '100%',
    zIndex: theme.zIndex.drawer + 1,
    background: 0,
    backgroundColor: theme.palette.background.nav,
    paddingTop: theme.spacing(0.2),
  },
  logoContainer: { marginLeft: -10 },
  logo: {
    cursor: 'pointer',
    height: 35,
  },
  barRight: {
    position: 'absolute',
    top: 15,
    right: 15,
    verticalAlign: 'middle',
    height: '100%',
  },
}));

const TopBar: FunctionComponent = () => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState<MenuProps['anchorEl']>(null);
  const dispatch = useAppDispatch();
  const handleOpen = (event: ReactMouseEvent) => {
    setOpen(true);
    setAnchorEl(event.currentTarget);
  };
  const handleClose = () => {
    setOpen(false);
    setAnchorEl(null);
  };
  const handleLogout = async () => {
    await dispatch(logout());
    navigate('/');
    setOpen(false);
  };
  return (
    <AppBar
      position="fixed"
      className={classes.appBar}
      variant="elevation"
      elevation={1}
    >
      <Toolbar>
        <div className={classes.logoContainer}>
          <Link to="/private">
            <img src={theme.logo} alt="logo" className={classes.logo} />
          </Link>
        </div>
        <div className={classes.barRight}>
          <IconButton onClick={handleOpen} size="small">
            <AccountCircleOutlined />
          </IconButton>
          <Menu anchorEl={anchorEl} open={open} onClose={handleClose}>
            <MenuItem
              onClick={handleClose}
              component={Link}
              to="/private/profile"
            >
              {t('Profile')}
            </MenuItem>
            <MenuItem onClick={handleLogout}>{t('Logout')}</MenuItem>
          </Menu>
        </div>
      </Toolbar>
    </AppBar>
  );
};

export default TopBar;
