import {
  Button,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { searchScenarios } from '../../../actions/scenarios/scenario-actions';
import { useFormatter } from '../../../components/i18n';
import { type Scenario } from '../../../utils/api-types';

interface Props {
  // The contract of the version shown in the drawer.
  actionId: string;
}

const ThreatArsenalUseActions: FunctionComponent<Props> = ({ actionId }) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const [pickScenario, setPickScenario] = useState(false);
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [scenarioId, setScenarioId] = useState('');

  useEffect(() => {
    if (pickScenario && scenarios.length === 0) {
      searchScenarios({
        page: 0,
        size: 200,
        sorts: [{
          property: 'scenario_name',
          direction: 'ASC',
        }],
      }).then((response: { data: { content: Scenario[] } }) => setScenarios(response.data.content));
    }
  }, [pickScenario]);

  return (
    <div style={{
      display: 'flex',
      gap: 8,
    }}
    >
      <Button size="sm" priority="secondary" onClick={() => navigate(`/admin/atomic_testings/create/${actionId}`)}>
        {t('Create atomic testing')}
      </Button>
      <Button size="sm" priority="secondary" onClick={() => setPickScenario(true)}>
        {t('Add to a scenario')}
      </Button>
      <Dialog open={pickScenario} onOpenChange={setPickScenario}>
        <DialogContent size="sm">
          <DialogTitle>{t('Add to a scenario')}</DialogTitle>
          <DialogBody>
            <Select value={scenarioId} onValueChange={setScenarioId}>
              <SelectTrigger aria-label={t('Scenario')}>
                <SelectValue placeholder={t('Scenario')} />
              </SelectTrigger>
              <SelectContent>
                {scenarios.map(scenario => (
                  <SelectItem key={scenario.scenario_id} value={scenario.scenario_id}>{scenario.scenario_name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={() => setPickScenario(false)}>{t('Cancel')}</Button>
            <Button
              disabled={!scenarioId}
              onClick={() => navigate(`/admin/scenarios/${scenarioId}/injects/create/${actionId}`)}
            >
              {t('Add')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default ThreatArsenalUseActions;
