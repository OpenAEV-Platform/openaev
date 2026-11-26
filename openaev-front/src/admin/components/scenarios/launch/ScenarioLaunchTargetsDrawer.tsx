import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type InjectTargetsOutput } from '../../../../utils/api-types';
import InjectTargetsList from '../targets/InjectTargetsList';
import { isTargetedWith, NO_TARGETS, type ScenarioTargets } from '../targets/scenarioTargets';
import ScenarioTargetsDrawer from '../targets/ScenarioTargetsDrawer';
import ScenarioTargetsFields from '../targets/ScenarioTargetsFields';

interface Props {
  scenarioId: string;
  name: string;
  injects: InjectTargetsOutput[];
  onLaunch: (targets: ScenarioTargets) => Promise<unknown>;
  onClose: () => void;
}

// A launch with an inject still missing its target asks for one first; the targets go to the simulation only.
const ScenarioLaunchTargetsDrawer: FunctionComponent<Props> = ({ scenarioId, name, injects, onLaunch, onClose }) => {
  const { t } = useFormatter();
  const [targets, setTargets] = useState<ScenarioTargets>(NO_TARGETS);
  const kinds = [...new Set(injects.flatMap(inject => inject.inject_target_kinds))];

  return (
    <ScenarioTargetsDrawer
      overline={t('Launch')}
      title={name}
      submitLabel={t('Launch')}
      launches
      blocked={injects.some(inject => !isTargetedWith(inject, targets))}
      onSubmit={() => onLaunch(targets)}
      onClose={onClose}
    >
      <ScenarioTargetsFields
        intro={t('Targets chosen here are added to every inject that takes this kind of target, for this simulation only: the scenario keeps its own.')}
        value={targets}
        onChange={setTargets}
        kinds={kinds}
      />
      <InjectTargetsList scenarioId={scenarioId} injects={injects} targets={targets} />
    </ScenarioTargetsDrawer>
  );
};

export default ScenarioLaunchTargetsDrawer;
