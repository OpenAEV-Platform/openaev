import { Alert, Button, Chip, Spinner, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { Field } from '../../../../components/common/detail/EntityDetailCommon';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import PlatformIconGroup from '../../../../components/PlatformIconGroup';
import { type CatalogScenarioOutput, type CatalogScenarioRequirementOutput } from '../../../../utils/api-types';
import { catalogConnectorUrl } from '../../integrations/catalog_connectors/catalog-facets';
import CatalogScenarioAction from './CatalogScenarioAction';
import ScenarioCatalogFacts from './ScenarioCatalogFacts';
import useCatalogScenarioVersion from './useCatalogScenarioVersion';

interface Props {
  scenario: CatalogScenarioOutput | null;
  canInstall: boolean;
  onClose: () => void;
  onInstall: (scenario: CatalogScenarioOutput) => void;
}

// A scenario of the Available tab: what it runs and needs, before installing it.
const ScenarioCatalogDrawer: FunctionComponent<Props> = ({ scenario, canInstall, onClose, onInstall }) => {
  const { t, nsd } = useFormatter();
  const theme = useTheme();
  const missing = scenario?.catalog_scenario_missing_requirements ?? [];
  const { shown, loading, formValues } = useCatalogScenarioVersion(scenario);
  const statusLabels: Record<NonNullable<CatalogScenarioRequirementOutput['requirement_status']>, string> = {
    NOT_DEPLOYED: t('Not deployed'),
    NOT_RUNNING: t('Not running'),
  };
  const payloads = shown?.catalog_scenario_payloads ?? [];
  const row = {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
  } as const;
  const column = {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(1),
  } as const;

  return (
    <Drawer
      open={scenario !== null}
      handleClose={onClose}
      title={scenario ? (scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id) : ''}
    >
      {scenario && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(3),
        }}
        >
          <div style={{
            ...row,
            flexWrap: 'wrap',
          }}
          >
            <PlatformIconGroup platforms={scenario.catalog_scenario_platforms} width={16} />
            <Text variant="content-caption">
              {[
                `v${scenario.catalog_scenario_version}`,
                shown?.catalog_scenario_injects_count != null ? t('{count} injects', { count: shown.catalog_scenario_injects_count }) : null,
                scenario.catalog_scenario_published_at ? `${t('Published')} ${nsd(scenario.catalog_scenario_published_at)}` : null,
                scenario.catalog_scenario_author ? `${t('by')} ${scenario.catalog_scenario_author}` : null,
              ].filter(Boolean).join(' · ')}
            </Text>
          </div>
          {loading || !formValues
            ? <Spinner size="sm" />
            : <ScenarioCatalogFacts key={`${scenario.catalog_scenario_external_id}:${scenario.catalog_scenario_version}`} values={formValues} />}
          {missing.length > 0 && (
            <Field label={t('Missing to run it')}>
              <div style={column}>
                {missing.map(requirement => (
                  <Alert
                    key={requirement.requirement_label}
                    severity="warning"
                    elevation={1}
                    title={requirement.requirement_name}
                    action={(requirement.requirement_status || requirement.requirement_integration_id) && (
                      <div style={row}>
                        {requirement.requirement_status && <Chip label={statusLabels[requirement.requirement_status]} severity="high" />}
                        {requirement.requirement_integration_id && (
                          <Button asChild size="sm" priority="secondary">
                            <Link to={catalogConnectorUrl(requirement.requirement_integration_id)} target="_blank">{t('Enable integration')}</Link>
                          </Button>
                        )}
                      </div>
                    )}
                  />
                ))}
              </div>
            </Field>
          )}
          {payloads.length > 0 && (
            <Field label={t('Payloads')}>
              <div style={column}>
                {payloads.map(payload => (
                  <Text key={`${payload.catalog_payload_external_id}:${payload.catalog_payload_version}`} variant="content-base">
                    {`${payload.catalog_payload_name ?? payload.catalog_payload_external_id} · v${payload.catalog_payload_version}`}
                  </Text>
                ))}
              </div>
            </Field>
          )}
          {shown?.catalog_scenario_changelog && (
            <Field label={t('What\'s new in v{version}', { version: shown.catalog_scenario_version })}>
              {shown.catalog_scenario_changelog}
            </Field>
          )}
          <div style={{
            ...row,
            justifyContent: 'flex-end',
          }}
          >
            <Button type="button" priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
            <CatalogScenarioAction scenario={scenario} canInstall={canInstall} onInstall={() => onInstall(scenario)} />
          </div>
        </div>
      )}
    </Drawer>
  );
};

export default ScenarioCatalogDrawer;
