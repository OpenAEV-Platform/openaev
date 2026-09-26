import { Button } from '@filigran/design-system';
import { VpnKeyOutlined } from '@mui/icons-material';
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
    <Button asChild priority="secondary">
      <a href={providerUri}>
        <VpnKeyOutlined fontSize="small" />
        <span>{t(providerName)}</span>
      </a>
    </Button>
  );
};

export default LoginSSOButton;
