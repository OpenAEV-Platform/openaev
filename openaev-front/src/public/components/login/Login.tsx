import { Box, Checkbox, Paper, Stack, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import Markdown from 'react-markdown';

import { askToken, checkKerberos } from '../../../actions/Application';
import { type LoggedHelper } from '../../../actions/helper';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { useAppDispatch } from '../../../utils/hooks';
import { isNotEmptyField } from '../../../utils/utils';
import LoginError from './LoginError';
import LoginForm from './LoginForm';
import LoginLayout from './LoginLayout';
import LoginSSOButton from './LoginSSOButton';
import Reset from './Reset';

/**
 * Login page aligned with OpenCTI: split-screen layout (form column +
 * themable aside), 500px content stack with consent / login message / form
 * card, and SSO buttons in a centered row below the card.
 */
const Login = () => {
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
  }, []);
  const onSubmit = (data: {
    username: string;
    password: string;
  }) => dispatch(askToken(data.username, data.password));

  // POLICIES
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
    // Auto scroll to bottom of unhidden/re-hidden login options.
    window.setTimeout(() => {
      const scrollingElement = document.scrollingElement ?? document.body;
      scrollingElement.scrollTop = scrollingElement.scrollHeight;
    }, 1);
  };

  const consentOk = !isConsentMessage || (isConsentMessage && checked);
  const ssoProviders = [...(openidProviders ?? []), ...(saml2Providers ?? [])];

  return (
    <LoginLayout>
      <Stack
        gap={1}
        sx={{
          width: '100%',
          maxWidth: 500,
          paddingInline: 2,
        }}
      >
        {isConsentMessage && (
          <Paper
            variant="outlined"
            sx={{
              padding: 2,
              borderRadius: 1,
              textAlign: 'center',
            }}
          >
            <Markdown>{consentMessage}</Markdown>
            <Box display="flex" justifyContent="center" alignItems="center">
              <Markdown>{consentConfirmText}</Markdown>
              <Checkbox
                name="consent"
                edge="start"
                onChange={handleChange}
                style={{ margin: 0 }}
              >
              </Checkbox>
            </Box>
          </Paper>
        )}
        {isLoginMessage && (
          <Typography
            component="div"
            textAlign="center"
            variant="body2"
            sx={{
              maxHeight: window.innerHeight * 0.25,
              overflowY: 'auto',
              marginBottom: 1,
            }}
          >
            <Markdown>{loginMessage}</Markdown>
          </Typography>
        )}
        {consentOk && (
          <>
            {isLocal && !reset && (
              <Paper
                elevation={0}
                sx={{
                  padding: 3,
                  borderRadius: 1,
                  backgroundColor: 'background.secondary',
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <div style={{ minHeight: 170 }}>
                  <LoginForm onSubmit={onSubmit} onResetPassword={() => setReset(true)} />
                </div>
              </Paper>
            )}
            {isLocal && reset && <Reset onCancel={() => setReset(false)} />}
            {(isOpenId || isSaml2) && ssoProviders.length > 0 && (
              <Stack
                mt={3}
                direction="row"
                justifyContent="center"
                flexWrap="wrap"
                gap={1}
              >
                {ssoProviders.map(provider => (
                  <LoginSSOButton
                    key={provider.provider_name}
                    providerName={provider.provider_login ?? ''}
                    providerUri={provider.provider_uri ?? ''}
                  />
                ))}
              </Stack>
            )}
            <LoginError />
          </>
        )}
      </Stack>
    </LoginLayout>
  );
};

export default Login;
