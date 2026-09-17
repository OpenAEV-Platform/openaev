import { Button } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import { getCurrentTenantId } from '../../../utils/url-helper';
import { getUrl, isNotEmptyField } from '../../../utils/utils';

interface ImportFromHubButtonProps { serviceIdentifier: string }

const ImportFromHubButton = ({ serviceIdentifier }: ImportFromHubButtonProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { settings } = useAuth();
  if (!settings.xtm_hub_enable) {
    return null;
  }

  const tenantId = getCurrentTenantId();
  const importFromHubUrl = isNotEmptyField(settings?.xtm_hub_url)
    ? getUrl(
        `/redirect/${serviceIdentifier}?platform_id=${settings.platform_id}&tenant_id=${tenantId}`,
        settings?.xtm_hub_url,
      )
    : '';

  return (
    <Button variant="highlight" asChild style={{ marginLeft: theme.spacing(0.5) }}>
      <a href={importFromHubUrl} target="_blank" rel="noreferrer" title={t('Import from Hub')}>
        {t('Import from Hub')}
      </a>
    </Button>
  );
};

export default ImportFromHubButton;
