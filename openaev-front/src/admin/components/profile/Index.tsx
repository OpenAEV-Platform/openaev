import { Button, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useMemo } from 'react';

import type { UserHelper } from '../../../actions/helper';
import { meTokens, renewToken, updateMeInformation, updateMePassword, updateMeProfile } from '../../../actions/users/User';
import { SECTION_LABEL_SX } from '../../../components/common/detail/detailStyles';
import Paper from '../../../components/common/Paper';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type UpdateProfileInput, type User } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { countryOption } from '../../../utils/Option';
import PasswordForm, { type PasswordFormInput } from './PasswordForm';
import ProfileForm, { type ProfileFormInput } from './ProfileForm';
import UserExperienceForm, { type UserExperienceFormInput } from './UserExperienceForm';
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

  // Memoized so that saving one card does not re-create the initial values of the other one,
  // which would reset it and discard the values being edited.
  const userValues: User = useMemo(() => ({
    ...user,
    user_firstname: user.user_firstname ?? '',
    user_lastname: user.user_lastname ?? '',
    user_organization: user.user_organization ?? '',
    user_country: countryOption(user.user_country)?.id ?? '',
    user_phone: user.user_phone ?? '',
    user_phone2: user.user_phone2 ?? '',
    user_pgp_key: user.user_pgp_key ?? '',
  }), [user]);
  const experienceValues: UserExperienceFormInput = useMemo(() => ({
    user_lang: user.user_lang ?? '',
    user_theme: user.user_theme ?? '',
    user_home_dashboard: user.user_home_dashboard ?? '',
  }), [user.user_lang, user.user_theme, user.user_home_dashboard]);

  // /api/me/profile overwrites every attribute it declares and requires the mandatory ones,
  // so each form has to complete its payload with the values it does not display.
  const buildProfilePayload = (data: Partial<UpdateProfileInput>): UpdateProfileInput => ({
    user_email: userValues.user_email,
    user_firstname: userValues.user_firstname ?? '',
    user_lastname: userValues.user_lastname ?? '',
    user_organization: userValues.user_organization,
    user_country: userValues.user_country,
    ...experienceValues,
    ...data,
  });

  const onRenew = (tokenId: string) => dispatch(renewToken(tokenId));
  const onUpdateProfile = ({
    user_phone,
    user_phone2,
    user_pgp_key,
    ...identity
  }: ProfileFormInput) => dispatch(updateMeProfile(buildProfilePayload(identity), false))
    .then(() => dispatch(updateMeInformation({
      user_phone,
      user_phone2,
      user_pgp_key,
    })));
  const onUpdateExperience = (data: UserExperienceFormInput) => dispatch(updateMeProfile(buildProfilePayload(data)));
  const onUpdatePassword = (data: PasswordFormInput) => dispatch(
    updateMePassword(data.user_current_password, data.user_plain_password),
  );

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
        <ProfileForm
          onSubmit={onUpdateProfile}
          initialValues={userValues}
        />
      </Paper>
      <Paper>
        <Typography variant="h1" style={{ marginBottom: 20 }}>
          {t('User Experience')}
        </Typography>
        <UserExperienceForm
          onSubmit={onUpdateExperience}
          initialValues={experienceValues}
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
