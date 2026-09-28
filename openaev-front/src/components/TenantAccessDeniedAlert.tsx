import { Alert, AlertTitle, AppBar, Button, Toolbar } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { logout } from '../actions/Application';
import { APP_BASE_PATH } from '../utils/Environment';
import { useAppDispatch } from '../utils/hooks';
import { useFormatter } from './i18n';

// Shown when the tenant identifier in the URL is not one the current user belongs to.
// '/me' never resolves in that case, so this must be reachable without a fully
// authenticated user/settings context (see 'root.tsx').
const TenantAccessDeniedAlert = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  const handleLogout = async () => {
    await dispatch(logout());
    window.location.href = `${APP_BASE_PATH}/`;
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
        <AlertTitle>{t('Tenant access denied')}</AlertTitle>
        {t('You are not a member of this tenant. Please contact your administrator to request access.')}
      </Alert>
    </>
  );
};

export default TenantAccessDeniedAlert;
