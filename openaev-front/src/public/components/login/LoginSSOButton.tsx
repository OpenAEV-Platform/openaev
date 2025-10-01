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

  return (
    <Button
      component="a"
      href={providerUri}
      variant="outlined"
      color="secondary"
      size="small"
      startIcon={<VpnKeyOutlined />}
    >
      <span>{t(providerName)}</span>
    </Button>
  );
};

export default LoginSSOButton;
