import { Alert, AlertTitle, AppBar, Button, Toolbar } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { logout } from '../actions/Application';
import { useAppDispatch } from '../utils/hooks';
import { useFormatter } from './i18n';

const NoTenantAlert = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const theme = useTheme() as any;

  const handleLogout = async () => {
    await dispatch(logout());
    window.location.href = '/';
  };

  return (
    <>
      <AppBar position="static">
        <Toolbar sx={{ justifyContent: 'space-between' }}>
          <img
            src={theme.logo}
            alt="logo"
            style={{ height: 25 }}
          />
          <Button color="inherit" onClick={handleLogout}>
            {t('Logout')}
          </Button>
        </Toolbar>
      </AppBar>
      <Alert
        severity="warning"
        sx={{
          display: 'flex',
          alignItems: 'center',
          margin: 2,
        }}
      >
        <AlertTitle>{t('No tenant assigned')}</AlertTitle>
        {t('Your account is not attached to any tenant. Please contact your administrator to get access.')}
      </Alert>
    </>
  );
};

export default NoTenantAlert;
