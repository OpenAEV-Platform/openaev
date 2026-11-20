import { Button, Chip, Spinner, Text } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';

import { fetchCatalogScenarioVersions } from '../../../../actions/catalog/scenario-catalog-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import ItemMainFocus from '../../../../components/ItemMainFocus';
import ItemSeverity from '../../../../components/ItemSeverity';
import PlatformIcon from '../../../../components/PlatformIcon';
import { type CatalogScenarioOutput, type CatalogScenarioVersionOutput } from '../../../../utils/api-types';
import { scenarioCategoryLabel } from '../constants';
import ScenarioType from '../scenario/ScenarioType';
import { catalogScenarioType } from './catalogScenarioType';

interface Props {
  scenario: CatalogScenarioOutput | null;
  canInstall: boolean;
  onClose: () => void;
  onInstall: (scenario: CatalogScenarioOutput) => void;
}

const Section = ({ title, children }: {
  title: string;
  children: ReactNode;
}) => (
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  }}
  >
    <Text variant="title-xs">{title}</Text>
    {children}
  </div>
);

// A scenario of the Available tab: what it is and needs, before installing it.
const ScenarioCatalogDrawer: FunctionComponent<Props> = ({ scenario, canInstall, onClose, onInstall }) => {
  const { t, nsd } = useFormatter();
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

  const details: {
    label: string;
    value: ReactNode;
  }[] = scenario
    ? [
        {
          label: t('Version'),
          value: `v${scenario.catalog_scenario_version}`,
        },
        {
          label: t('Published'),
          value: scenario.catalog_scenario_published_at ? nsd(scenario.catalog_scenario_published_at) : '–',
        },
        {
          label: t('Author'),
          value: scenario.catalog_scenario_author ?? '–',
        },
        {
          label: t('Type'),
          value: <ScenarioType type={catalogScenarioType(scenario)} />,
        },
        {
          label: t('Category'),
          value: category ? t(scenarioCategoryLabel(category)) : '–',
        },
        {
          label: t('Main focus'),
          value: ofVersion(shown?.catalog_scenario_main_focus
            ? <ItemMainFocus mainFocus={shown.catalog_scenario_main_focus} label={t(shown.catalog_scenario_main_focus)} size="small" />
            : '–'),
        },
        {
          label: t('Severity'),
          value: ofVersion(shown?.catalog_scenario_severity
            ? <ItemSeverity severity={shown.catalog_scenario_severity} label={t(shown.catalog_scenario_severity)} />
            : '–'),
        },
        {
          label: t('Injects'),
          value: ofVersion(shown?.catalog_scenario_injects_count ?? '–'),
        },
        {
          label: t('Platforms'),
          value: scenario.catalog_scenario_platforms.length > 0
            ? (
                <div style={{
                  display: 'flex',
                  gap: 4,
                }}
                >
                  {scenario.catalog_scenario_platforms.map(platform => <PlatformIcon key={platform} width={16} platform={platform} tooltip />)}
                </div>
              )
            : '–',
        },
      ]
    : [];

  return (
    <Drawer
      open={scenario !== null}
      handleClose={onClose}
      title={scenario ? (scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id) : ''}
      headerActions={canInstall && scenario && (
        <Button disabled={missing.length > 0} onClick={() => onInstall(scenario)}>{t('Install')}</Button>
      )}
    >
      {scenario && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 24,
        }}
        >
          {scenario.catalog_scenario_description && <Text variant="content-base">{scenario.catalog_scenario_description}</Text>}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
            gap: 16,
          }}
          >
            {details.map(({ label, value }) => (
              <div
                key={label}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                }}
              >
                <Text variant="content-caption">{label}</Text>
                {typeof value === 'object' ? value : <Text variant="content-base">{value}</Text>}
              </div>
            ))}
          </div>
          {missing.length > 0 && (
            <Section title={t('Requirements missing')}>
              <div style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 8,
              }}
              >
                {missing.map(requirement => <Chip key={requirement} label={requirement} severity="high" />)}
              </div>
            </Section>
          )}
          {shown?.catalog_scenario_changelog && (
            <Section title={t('What\'s new in v{version}', { version: shown.catalog_scenario_version })}>
              <Text variant="content-base">{shown.catalog_scenario_changelog}</Text>
            </Section>
          )}
        </div>
      )}
    </Drawer>
  );
};

export default ScenarioCatalogDrawer;
