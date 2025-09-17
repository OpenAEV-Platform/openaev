import type { ButtonProps } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import { isNotEmptyField } from '../../../utils/utils';
import GradientButton from './GradientButton';

interface ImportFromHubButtonProps extends ButtonProps { serviceIdentifier: string }

const ImportFromHubButton = ({ serviceIdentifier }: ImportFromHubButtonProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { settings } = useAuth();
  if (!settings.xtm_hub_enable) {
    return null;
  }

  const importFromHubUrl = isNotEmptyField(settings?.xtm_hub_url)
    ? `${settings?.xtm_hub_url}/redirect/${serviceIdentifier}?obas_instance_id=${settings.platform_id}`
    : '';

  return (
    <GradientButton
      size="small"
      style={{ marginLeft: theme.spacing(0.5) }}
      href={importFromHubUrl}
      target="_blank"
      title={t('Import from Hub')}
    >
      <span className="text">{t('Import from Hub')}</span>
    </GradientButton>
  );
};

export default ImportFromHubButton;
