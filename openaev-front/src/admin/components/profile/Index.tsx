import { Button, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import type { UserHelper } from '../../../actions/helper';
import { meTokens, renewToken, updateMeInformation, updateMePassword, updateMeProfile } from '../../../actions/users/User';
import { SECTION_LABEL_SX } from '../../../components/common/detail/detailStyles';
import Paper from '../../../components/common/Paper';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type UpdateProfileInput, type UpdateUserInfoInput, type User } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { countryOption } from '../../../utils/Option';
import PasswordForm, { type PasswordFormInput } from './PasswordForm';
import ProfileForm from './ProfileForm';
import UserForm from './UserForm';
import XtmOneMcpAccess from './XtmOneMcpAccess';

const Index = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(meTokens());
  });
  const { user, tokens } = useHelper((helper: UserHelper) => ({
    user: helper.getMe(),
    tokens: helper.getMeTokens(),
  }));

  const onRenew = (tokenId: string) => dispatch(renewToken(tokenId));
  const onUpdateProfile = (data: UpdateProfileInput) => dispatch(updateMeProfile(data));
  const onUpdateInformation = (data: UpdateUserInfoInput) => dispatch(updateMeInformation(data));
  const onUpdatePassword = (data: PasswordFormInput) => dispatch(
    updateMePassword(data.user_current_password, data.user_plain_password),
  );

  const profileValues: User = {
    ...user,
    user_firstname: user.user_firstname ?? '',
    user_lastname: user.user_lastname ?? '',
    user_lang: user.user_lang ?? '',
    user_theme: user.user_theme ?? '',
    user_organization: user.user_organization ?? '',
    user_country: countryOption(user.user_country)?.id ?? '',
    user_home_dashboard: user.user_home_dashboard ?? '',
  };
  const informationValues: UpdateUserInfoInput = {
    user_phone: user.user_phone ?? '',
    user_phone2: user.user_phone2 ?? '',
    user_pgp_key: user.user_pgp_key ?? '',
  };
  const userToken = tokens.length > 0 ? tokens[0] : undefined;

  return (
    <div style={{
      width: 800,
      margin: '0 auto',
      display: 'grid',
      gap: theme.spacing(3),
    }}
    >
      <Paper>
        <Typography variant="h1" style={{ marginBottom: 20 }}>
          {t('Profile')}
        </Typography>
        <UserForm
          onSubmit={onUpdateProfile}
          initialValues={profileValues}
        />
      </Paper>
      <Paper>
        <Typography variant="h1" style={{ marginBottom: 20 }}>
          {t('Information')}
        </Typography>
        <ProfileForm
          onSubmit={onUpdateInformation}
          initialValues={informationValues}
        />
      </Paper>
      {!user.user_is_external && (
        <Paper>
          <Typography variant="h1" style={{ marginBottom: 20 }}>
            {t('Password')}
          </Typography>
          <PasswordForm onSubmit={onUpdatePassword} />
        </Paper>
      )}
      <Paper>
        <Typography variant="h1" style={{ marginBottom: 20 }}>
          {t('API access')}
        </Typography>
        <Typography variant="body1">
          {t(
            'The OpenAEV API relies on the REST standard. The token must be passed into the HTTP header',
          )}
          {' '}
          <strong>{t('Authorization')}</strong>
          .
        </Typography>
        <Typography
          gutterBottom={true}
          sx={{
            ...SECTION_LABEL_SX,
            mt: 2.5,
          }}
        >
          {t('Token key')}
        </Typography>
        <pre>{userToken?.token_value}</pre>
        <Button
          variant="contained"
          color="primary"
          component="a"
          onClick={() => userToken && onRenew(userToken.token_id)}
        >
          {t('RENEW')}
        </Button>
        <Typography
          gutterBottom={true}
          sx={{
            ...SECTION_LABEL_SX,
            mt: 2.5,
          }}
        >
          {t('Example')}
        </Typography>
        {/* eslint-disable-next-line i18next/no-literal-string */}
        <pre>
          GET /api/exercises
          {/* eslint-disable-next-line i18next/no-literal-string */}
          <br />
          Content-Type: application/json
          {/* eslint-disable-next-line i18next/no-literal-string */}
          <br />
          Authorization: ******
          {' '}
          {userToken?.token_value}
        </pre>

        <Button
          variant="contained"
          color="primary"
          component="a"
          href="/swagger-ui/index.html"
        >
          {t('API specifications')}
        </Button>
      </Paper>
      <XtmOneMcpAccess />
    </div>
  );
};

export default Index;
