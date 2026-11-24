import { Button, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { Field } from '../../../../components/common/detail/EntityDetailCommon';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import PlatformIconGroup from '../../../../components/PlatformIconGroup';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { type ScenarioFormInput } from '../ScenarioForm';
import CatalogScenarioAction from './CatalogScenarioAction';
import MissingRequirementAlert from './MissingRequirementAlert';
import ScenarioCatalogFacts from './ScenarioCatalogFacts';

interface Props {
  scenario: CatalogScenarioOutput | null;
  canInstall: boolean;
  onClose: () => void;
  onInstall: (scenario: CatalogScenarioOutput) => void;
}

const factsOf = (scenario: CatalogScenarioOutput): ScenarioFormInput => ({
  scenario_name: scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id,
  scenario_description: scenario.catalog_scenario_description ?? '',
  scenario_category: scenario.catalog_scenario_category,
  scenario_main_focus: scenario.catalog_scenario_main_focus,
  scenario_severity: scenario.catalog_scenario_severity as ScenarioFormInput['scenario_severity'],
  scenario_tags: [],
});

// A scenario of the Available tab: what it runs and needs, before installing it.
const ScenarioCatalogDrawer: FunctionComponent<Props> = ({ scenario, canInstall, onClose, onInstall }) => {
  const { t, nsd } = useFormatter();
  const theme = useTheme();
  const missing = scenario?.catalog_scenario_missing_requirements ?? [];
  const payloads = scenario?.catalog_scenario_payloads ?? [];
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
                scenario.catalog_scenario_injects_count != null ? t('{count} injects', { count: String(scenario.catalog_scenario_injects_count) }) : null,
                scenario.catalog_scenario_published_at ? `${t('Published')} ${nsd(scenario.catalog_scenario_published_at)}` : null,
                scenario.catalog_scenario_author ? `${t('by')} ${scenario.catalog_scenario_author}` : null,
              ].filter(Boolean).join(' · ')}
            </Text>
          </div>
          <ScenarioCatalogFacts key={`${scenario.catalog_scenario_external_id}:${scenario.catalog_scenario_version}`} values={factsOf(scenario)} />
          {missing.length > 0 && (
            <Field label={t('Missing to run it')}>
              <div style={column}>
                {missing.map(requirement => (
                  <MissingRequirementAlert key={requirement.requirement_label} requirement={requirement} severity="warning" />
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
          {scenario.catalog_scenario_changelog && (
            <Field label={t('What\'s new in v{version}', { version: scenario.catalog_scenario_version })}>
              {scenario.catalog_scenario_changelog}
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
