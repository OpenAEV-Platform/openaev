import { VpnKeyOutlined } from '@mui/icons-material';
import { Button } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';

interface LoginSSOButtonProperties {
  providerUri: string;
  providerName: string;
}

const LoginSSOButton: FunctionComponent<LoginSSOButtonProperties> = ({
  providerUri,
  providerName,
}) => {
  const { t } = useFormatter();

  // Aligned with OpenCTI's ExternalAuthButton: outlined primary button with a
  // key icon, rendered as a plain link to the SSO provider.
  return (
    <Button
      component="a"
      href={providerUri}
      variant="outlined"
      color="primary"
      startIcon={<VpnKeyOutlined fontSize="small" />}
    >
      <span>{t(providerName)}</span>
    </Button>
  );
};

export default LoginSSOButton;
