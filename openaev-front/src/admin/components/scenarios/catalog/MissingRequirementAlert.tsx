import { Alert, type AlertSeverity, Button } from '@filigran/design-system';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioRequirementOutput } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { catalogConnectorUrl } from '../../integrations/catalog_connectors/catalog-facets';

interface Props {
  requirement: CatalogScenarioRequirementOutput;
  severity: AlertSeverity;
}

// Something a catalogue scenario lacks on this platform, with the integration to enable when there is one.
const MissingRequirementAlert: FunctionComponent<Props> = ({ requirement, severity }) => {
  const { t } = useFormatter();
  const canEnable = useAbility().can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const integrationId = requirement.requirement_integration_id;
  const name = { name: requirement.requirement_name };
  return (
    <Alert
      elevation={1}
      severity={severity}
      title={requirement.requirement_status === 'NOT_RUNNING'
        ? t('{name} is not running', name)
        : t('{name} is missing on this platform', name)}
      description={t('The injects that need it cannot run.')}
      action={integrationId && canEnable
        ? (
            <Button asChild size="sm" priority="secondary">
              <Link to={catalogConnectorUrl(integrationId)} target="_blank">{t('Enable integration')}</Link>
            </Button>
          )
        : undefined}
    />
  );
};

export default MissingRequirementAlert;
