import { Button, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { installCatalogScenario } from '../../../../actions/catalog/scenario-catalog-actions';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { SCENARIO_TYPE_CHAINED } from '../scenario/ScenarioType';
import { NO_TARGETS, type ScenarioTargets, toTargetsInput } from '../targets/scenarioTargets';
import ScenarioTargetsDrawer from '../targets/ScenarioTargetsDrawer';
import ScenarioTargetsFields from '../targets/ScenarioTargetsFields';
import { catalogScenarioType } from './catalogScenarioType';
import { hasScope, NO_SCOPE, toChainedInstallInput } from './chainedScope';
import ChainedScopeFields from './ChainedScopeFields';
import MissingRequirementAlert from './MissingRequirementAlert';
import useMissingRequirements from './useMissingRequirements';

interface Props {
  scenario: CatalogScenarioOutput;
  // Install, then launch the installed scenario.
  thenLaunch: boolean;
  onClose: () => void;
  onInstalled: (scenario: CatalogScenarioOutput, scenarioId: string) => void;
}

// The injects of a time-based scenario take the chosen targets; a chained one acts inside the chosen scope.
const CatalogScenarioInstallDrawer: FunctionComponent<Props> = ({ scenario, thenLaunch, onClose, onInstalled }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const chained = catalogScenarioType(scenario) === SCENARIO_TYPE_CHAINED;
  const { missing, checking, checkAgain } = useMissingRequirements(scenario);
  const [targets, setTargets] = useState<ScenarioTargets>(NO_TARGETS);
  const [scope, setScope] = useState(NO_SCOPE);

  const install = () => installCatalogScenario(scenario, chained
    ? toChainedInstallInput(scope)
    : {
        scenario_type: 'time-based',
        ...toTargetsInput(targets),
      }).then(({ installed_id }) => onInstalled(scenario, installed_id));

  return (
    <ScenarioTargetsDrawer
      overline={thenLaunch ? t('Install and launch') : t('Install')}
      title={scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id}
      version={scenario.catalog_scenario_version}
      submitLabel={thenLaunch ? t('Install and launch') : t('Install')}
      launches={thenLaunch}
      blocked={missing.length > 0 || (chained && !hasScope(scope))}
      onSubmit={install}
      onClose={onClose}
    >
      {missing.length > 0 && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(1),
        }}
        >
          <Text variant="content-base">{t('Enable the missing integrations, then check again.')}</Text>
          {missing.map(requirement => (
            <MissingRequirementAlert key={requirement.requirement_label} requirement={requirement} severity="error" />
          ))}
          <div>
            <Button type="button" size="sm" priority="secondary" loading={checking} onClick={checkAgain}>{t('Check again')}</Button>
          </div>
        </div>
      )}
      {chained
        ? <ChainedScopeFields value={scope} onChange={setScope} />
        : (
            <ScenarioTargetsFields
              intro={t('Targets chosen here are added to every inject that takes this kind of target; existing targets are kept. They are saved on the installed scenario.')}
              value={targets}
              onChange={setTargets}
            />
          )}
    </ScenarioTargetsDrawer>
  );
};

export default CatalogScenarioInstallDrawer;
