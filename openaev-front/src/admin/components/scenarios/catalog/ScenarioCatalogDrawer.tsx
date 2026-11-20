import { Chip, Spinner, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';

import { fetchCatalogScenarioVersions } from '../../../../actions/catalog/scenario-catalog-actions';
import { Field } from '../../../../components/common/detail/EntityDetailCommon';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemMainFocus from '../../../../components/ItemMainFocus';
import ItemSeverity from '../../../../components/ItemSeverity';
import PlatformIconGroup from '../../../../components/PlatformIconGroup';
import { type CatalogScenarioOutput, type CatalogScenarioVersionOutput } from '../../../../utils/api-types';
import { scenarioCategoryLabel } from '../constants';
import ScenarioType from '../scenario/ScenarioType';
import CatalogScenarioAction from './CatalogScenarioAction';
import { catalogScenarioType } from './catalogScenarioType';

interface Props {
  scenario: CatalogScenarioOutput | null;
  canInstall: boolean;
  onClose: () => void;
  onInstall: (scenario: CatalogScenarioOutput) => void;
}

// A scenario of the Available tab: what it is and needs, before installing it.
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

  return (
    <Drawer
      open={scenario !== null}
      handleClose={onClose}
      title={scenario ? (scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id) : ''}
      headerActions={scenario && <CatalogScenarioAction scenario={scenario} canInstall={canInstall} onInstall={() => onInstall(scenario)} />}
    >
      {scenario && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(3),
        }}
        >
          {scenario.catalog_scenario_description && <Text variant="content-base">{scenario.catalog_scenario_description}</Text>}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
            gap: theme.spacing(2),
          }}
          >
            <Field label={t('Version')}>{`v${scenario.catalog_scenario_version}`}</Field>
            <Field label={t('Published')}>{scenario.catalog_scenario_published_at ? nsd(scenario.catalog_scenario_published_at) : '–'}</Field>
            <Field label={t('Author')}>{scenario.catalog_scenario_author ?? '–'}</Field>
            <Field label={t('Type')}><ScenarioType type={catalogScenarioType(scenario)} /></Field>
            <Field label={t('Category')}>
              {category ? <ItemCategory category={category} label={t(scenarioCategoryLabel(category))} /> : '–'}
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
            <Field label={t('Injects')}>{ofVersion(shown?.catalog_scenario_injects_count ?? '–')}</Field>
            <Field label={t('Platforms')}><PlatformIconGroup platforms={scenario.catalog_scenario_platforms} width={16} /></Field>
          </div>
          {missing.length > 0 && (
            <Field label={t('Requirements missing')}>
              <div style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: theme.spacing(1),
              }}
              >
                {missing.map(requirement => <Chip key={requirement} label={requirement} severity="high" />)}
              </div>
            </Field>
          )}
          {shown?.catalog_scenario_changelog && (
            <Field label={t('What\'s new in v{version}', { version: shown.catalog_scenario_version })}>
              {shown.catalog_scenario_changelog}
            </Field>
          )}
        </div>
      )}
    </Drawer>
  );
};

export default ScenarioCatalogDrawer;
