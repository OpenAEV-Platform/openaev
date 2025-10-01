import { Button, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useDispatch } from 'react-redux';

import { meTokens, renewToken, updateMeInformation, updateMePassword, updateMeProfile } from '../../../actions/User';
import Paper from '../../../components/common/Paper';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { countryOption } from '../../../utils/Option';
import PasswordForm from './PasswordForm';
import ProfileForm from './ProfileForm';
import UserForm from './UserForm';

const Index = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useDispatch();
  useDataLoader(() => {
    dispatch(meTokens());
  });
  const { user, tokens } = useHelper(helper => ({
    user: helper.getMe(),
    tokens: helper.getMeTokens(),
  }));
  const onRenew = tokenId => dispatch(renewToken(tokenId));
  const onUpdate = (data) => {
    const inputValues = R.pipe(
      R.assoc(
        'user_organization',
        data.user_organization && data.user_organization.id
          ? data.user_organization.id
          : data.user_organization,
      ),
      R.assoc(
        'user_country',
        data.user_country && data.user_country.id
          ? data.user_country.id
          : data.user_country,
      ),
    )(data);
    return dispatch(updateMeProfile(inputValues));
  };
  const onUpdateInformation = data => dispatch(updateMeInformation(data));
  const onUpdatePassword = data => dispatch(
    updateMePassword(data.user_current_password, data.user_plain_password),
  );
  const initialValues = {
    user_firstname: user.user_firstname,
    user_lastname: user.user_lastname,
    user_email: user.user_email,
    user_phone: user.user_phone,
    user_phone2: user.user_phone2,
    user_pgp_key: user.user_pgp_key,
    user_lang: user.user_lang,
    user_theme: user.user_theme,
    user_is_external: user.user_is_external,
    user_organization: user.user_organization ?? '',
    user_country: countryOption(user.user_country)?.id ?? '',
  };
  const userToken = tokens.length > 0 ? R.head(tokens) : undefined;
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
        <UserForm onSubmit={onUpdate} initialValues={initialValues} />
      </Paper>
      <Paper>
        <Typography variant="h1" style={{ marginBottom: 20 }}>
          {t('Information')}
        </Typography>
        <ProfileForm
          onSubmit={onUpdateInformation}
          initialValues={initialValues}
        />
      </Paper>
      {!initialValues.user_is_external && (
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
          variant="h4"
          gutterBottom={true}
          style={{ marginTop: 20 }}
        >
          {t('Token key')}
        </Typography>
        <pre>{userToken?.token_value}</pre>
        <Button
          variant="contained"
          color="primary"
          component="a"
          onClick={() => onRenew(userToken?.token_id)}
        >
          {t('RENEW')}
        </Button>
        <Typography
          variant="h4"
          gutterBottom={true}
          style={{ marginTop: 20 }}
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
          Authorization: Bearer
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
    </div>
  );
};

export default Index;
