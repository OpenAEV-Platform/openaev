import { Text } from '@filigran/design-system';
import { MenuItem, Select } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { searchSimulationByIdAsOptions } from '../../../../../actions/simulations/simulation-action';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Scenario } from '../../../../../utils/api-types';
import { type Option } from '../../../../../utils/Option';
import SimulationMitigations from '../../../simulations/simulation/mitigations/SimulationMitigations';

// PROTOTYPE - MOCK DATA ONLY. Scenario level: a scenario has no result of its own, so like the
// Attack Path a simulation picker (default: the most recent run) chooses which simulation's
// mitigations are listed.
const ScenarioMitigations: FunctionComponent = () => {
  const { t } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  const ids = useMemo(() => scenario?.scenario_exercises ?? [], [scenario?.scenario_exercises]);
  const [options, setOptions] = useState<Option[]>([]);
  const [selected, setSelected] = useState<string>('');

  useEffect(() => {
    if (ids.length === 0) return;
    searchSimulationByIdAsOptions(ids).then((result: { data: Option[] }) => {
      setOptions(result.data ?? []);
      setSelected(prev => prev || result.data?.[0]?.id || '');
    });
  }, [ids]);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
      }}
      >
        <Text variant="content-base-bold">{t('Simulation')}</Text>
        <Select size="small" value={selected} onChange={event => setSelected(event.target.value)} style={{ minWidth: 280 }} displayEmpty>
          {options.length === 0 && <MenuItem value="">{t('No simulation')}</MenuItem>}
          {options.map(option => <MenuItem key={option.id} value={option.id}>{option.label}</MenuItem>)}
        </Select>
      </div>
      <SimulationMitigations key={selected} simulationLabel={options.find(option => option.id === selected)?.label} />
    </div>
  );
};

export default ScenarioMitigations;
