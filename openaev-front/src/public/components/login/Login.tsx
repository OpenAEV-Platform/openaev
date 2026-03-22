import { Box, Checkbox, Stack } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import Markdown from 'react-markdown';

import { askToken, checkKerberos } from '../../../actions/Application';
import { type LoggedHelper } from '../../../actions/helper';
import Card from '../../../components/common/card/Card';
import { useFormatter } from '../../../components/i18n';
import logoDark from '../../../static/images/logo_text_dark.png';
import logoLight from '../../../static/images/logo_text_light.png';
import { useHelper } from '../../../store';
import { fileUri } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import { isNotEmptyField } from '../../../utils/utils';
import LoginError from './LoginError';
import LoginForm from './LoginForm';
import LoginLayout from './LoginLayout';
import LoginSSOButton from './LoginSSOButton';
import Reset from './Reset';

const Login = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { settings } = useHelper((helper: LoggedHelper) => {
    return { settings: helper.getPlatformSettings() };
  });

  const {
    auth_openid_enable: isOpenId,
    auth_saml2_enable: isSaml2,
    auth_local_enable: isLocal,
  } = settings;
  const {
    platform_openid_providers: openidProviders,
    platform_saml2_providers: saml2Providers,
  } = settings;
  const [reset, setReset] = useState(false);

  useEffect(() => {
    dispatch(checkKerberos());
  });

  const onSubmit = (data: {
    username: string;
    password: string;
  }) => dispatch(askToken(data.username, data.password));

  const loginLogo = theme.palette.mode === 'dark'
    ? settings?.platform_dark_theme?.logo_login_url
    : settings?.platform_light_theme?.logo_login_url;

  const isWhitemarkEnable = settings.platform_whitemark === 'true'
    && settings.platform_license?.license_is_validated === true;

  const loginMessage = settings.platform_policies?.platform_login_message;
  const consentMessage = settings.platform_policies?.platform_consent_message;
  const consentConfirmText = settings.platform_policies?.platform_consent_confirm_text
    ? settings.platform_policies.platform_consent_confirm_text
    : t('I have read and comply with the above statement');
  const isLoginMessage = isNotEmptyField(loginMessage);
  const isConsentMessage = isNotEmptyField(consentMessage);
  const [checked, setChecked] = useState(false);
  const handleChange = () => {
    setChecked(!checked);
    window.setTimeout(() => {
      const scrollingElement = document.scrollingElement ?? document.body;
      scrollingElement.scrollTop = scrollingElement.scrollHeight;
    }, 1);
  };

  return (
    <LoginLayout isWhitemarkEnable={isWhitemarkEnable}>
      <img
        src={loginLogo && loginLogo.length > 0 ? loginLogo : fileUri(
          theme.palette.mode === 'dark' ? logoDark : logoLight,
        )}
        alt="logo"
        style={{ width: 280 }}
      />
      <Stack gap={1} sx={{ width: 500 }}>
        {isConsentMessage && (
          <Card padding="small" fullHeight={false}>
            <Markdown>{consentMessage}</Markdown>
            <Box display="flex" justifyContent="center" alignItems="center">
              <Markdown>{consentConfirmText}</Markdown>
              <Checkbox
                name="consent"
                edge="start"
                onChange={handleChange}
                style={{ margin: 0 }}
              />
            </Box>
          </Card>
        )}
        {(!isConsentMessage || (isConsentMessage && checked)) && (
          <>
            {isLocal && !reset && (
              <Card
                padding="default"
                fullHeight={false}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                {isLoginMessage && (
                  <Box sx={{ mb: 2 }}>
                    <Markdown>{loginMessage}</Markdown>
                  </Box>
                )}
                <LoginForm
                  onSubmit={onSubmit}
                  onForgotPassword={() => setReset(true)}
                />
              </Card>
            )}
            {isLocal && reset && <Reset onCancel={() => setReset(false)} />}
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 2.5,
                mt: 1,
              }}
            >
              {(isOpenId || isSaml2) && [...(openidProviders ?? []),
                ...(saml2Providers ?? [])].map(
                provider => (
                  <LoginSSOButton
                    key={provider.provider_name}
                    providerName={provider.provider_login}
                    providerUri={provider.provider_uri}
                  />
                ),
              )}
              <LoginError />
            </Box>
          </>
        )}
      </Stack>
    </LoginLayout>
  );
};

export default Login;
