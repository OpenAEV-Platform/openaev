import { Alert, Button, Spinner, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { fetchCatalogScenarioVersions } from '../../../../actions/catalog/scenario-catalog-actions';
import { Field } from '../../../../components/common/detail/EntityDetailCommon';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemMainFocus from '../../../../components/ItemMainFocus';
import ItemSeverity from '../../../../components/ItemSeverity';
import PlatformIconGroup from '../../../../components/PlatformIconGroup';
import { type CatalogScenarioOutput, type CatalogScenarioVersionOutput } from '../../../../utils/api-types';
import { catalogConnectorUrl } from '../../integrations/catalog_connectors/catalog-facets';
import { scenarioCategories } from '../constants';

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
  const category = scenario?.catalog_scenario_category;
  const [shown, setShown] = useState<CatalogScenarioVersionOutput | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setShown(null);
    if (!scenario) return undefined;
    let active = true;
    setLoading(true);
    fetchCatalogScenarioVersions(scenario.catalog_scenario_external_id)
      .then((versions) => {
        if (active) setShown(versions.find(version => version.catalog_scenario_version === scenario.catalog_scenario_version) ?? null);
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [scenario]);

  const ofVersion = (value: ReactNode): ReactNode => (loading ? <Spinner size="sm" /> : value);
  const payloads = shown?.catalog_scenario_payloads ?? [];
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
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: theme.spacing(1),
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
          {scenario.catalog_scenario_description && <Text variant="content-base">{scenario.catalog_scenario_description}</Text>}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
            gap: theme.spacing(2),
          }}
          >
            <Field label={t('Category')}>
              {category ? <ItemCategory category={category} label={t(scenarioCategories.get(category) ?? category)} /> : '–'}
            </Field>
            <Field label={t('Main focus')}>
              {ofVersion(shown?.catalog_scenario_main_focus
                ? <ItemMainFocus mainFocus={shown.catalog_scenario_main_focus} label={t(shown.catalog_scenario_main_focus)} size="small" />
                : '–')}
            </Field>
            <Field label={t('Severity')}>
              {ofVersion(shown?.catalog_scenario_severity
                ? <ItemSeverity severity={shown.catalog_scenario_severity} label={t(shown.catalog_scenario_severity)} />
                : '–')}
            </Field>
          </div>
          {missing.length > 0 && (
            <Field label={t('Missing to run it')}>
              <div style={column}>
                {missing.map(requirement => (
                  <Alert
                    key={requirement.requirement_label}
                    severity="warning"
                    elevation={1}
                    title={requirement.requirement_label}
                    action={requirement.requirement_integration_id && (
                      <Button asChild size="sm" priority="secondary">
                        <Link to={catalogConnectorUrl(requirement.requirement_integration_id)} target="_blank">{t('Enable integration')}</Link>
                      </Button>
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
            display: 'flex',
            justifyContent: 'flex-end',
            gap: theme.spacing(1),
          }}
          >
            <Button type="button" priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
            <Button type="button" disabled={!canInstall || missing.length > 0} onClick={() => onInstall(scenario)}>{t('Install')}</Button>
          </div>
        </div>
      )}
    </Drawer>
  );
};

export default ScenarioCatalogDrawer;
