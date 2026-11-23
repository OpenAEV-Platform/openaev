import { Button } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { fetchCatalogConnectorQuietly } from '../../../../actions/catalog/catalog-actions';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import DeployButton from './DeployButton';

// Where an integration without a deployment guide of its own is explained.
const DEPLOYMENT_DOCS = 'https://docs.openaev.io/latest/deployment/ecosystem/injectors/';

interface Props {
  catalogConnectorId: string;
  priority?: 'primary' | 'secondary';
}

// The fix for a missing integration in the launch check. Enterprise Edition deploys it from the platform:
// its page opens. Community deploys it by hand: its deployment guide, beside the Enterprise Edition Deploy.
const IntegrationFixButton: FunctionComponent<Props> = ({ catalogConnectorId, priority = 'secondary' }) => {
  const { t } = useFormatter();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const [guide, setGuide] = useState<string | null>(null);
  useEffect(() => {
    setGuide(null);
    if (isEnterpriseEdition) return;
    fetchCatalogConnectorQuietly(catalogConnectorId)
      .then(response => setGuide((response.data as CatalogConnectorOutput).catalog_connector_source_code || null))
      .catch(() => setGuide(null));
  }, [isEnterpriseEdition, catalogConnectorId]);

  if (isEnterpriseEdition) {
    return (
      <Button asChild size="sm" priority={priority}>
        <Link to={`/admin/integrations/catalog/${encodeURIComponent(catalogConnectorId)}`} target="_blank">
          {t('Enable integration')}
        </Link>
      </Button>
    );
  }
  // The guide unblocks the launch; Deploy, the same button as on the Integrations page, shows what the
  // Enterprise Edition adds (its dialog opens in Community).
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      flexWrap: 'wrap',
    }}
    >
      <Button asChild size="sm" priority={priority}>
        <a href={guide ?? DEPLOYMENT_DOCS} target="_blank" rel="noopener noreferrer">
          {t('Deployment guide')}
          <OpenInNewOutlined fontSize="small" style={{ marginLeft: 4 }} />
        </a>
      </Button>
      <DeployButton size="sm" deploymentCount={0} onDeployBtnClick={() => undefined} />
    </div>
  );
};

export default IntegrationFixButton;
