import { Paper } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { useParams } from 'react-router';

import { fetchScenario } from '../../../../../actions/scenarios/scenario-actions';
import { useFormatter } from '../../../../../components/i18n';
import { useAppDispatch } from '../../../../../utils/hooks';
import useScenarioPermissions from '../../../../../utils/permissions/useScenarioPermissions';
import ScenarioRevisionsTable from './ScenarioRevisionsTable';
import ScenarioUpdateDialog from './ScenarioUpdateDialog';
import { rollbackTarget, type ScenarioUpdateTarget, updateTarget } from './scenarioUpdateTargets';
import ScenarioVersionsTable from './ScenarioVersionsTable';
import useScenarioVersions from './useScenarioVersions';

// The catalogue versions to update to, then the content kept to roll back to.
const ScenarioVersions: FunctionComponent = () => {
  const { t } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: string };
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const { canManage } = useScenarioPermissions(scenarioId);
  const { versions, revisions, load } = useScenarioVersions(scenarioId);
  const [target, setTarget] = useState<ScenarioUpdateTarget | null>(null);

  const applied = () => {
    setTarget(null);
    dispatch(fetchScenario(scenarioId));
    load();
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(3),
    }}
    >
      <Paper title={t('Versions')} padding={16}>
        {versions && (
          <ScenarioVersionsTable
            versions={versions}
            canManage={canManage}
            onUpdate={version => setTarget(updateTarget(scenarioId, version, t))}
          />
        )}
      </Paper>
      <Paper title={t('Revisions')} padding={16}>
        <ScenarioRevisionsTable
          revisions={revisions}
          canManage={canManage}
          onRollBack={revision => setTarget(rollbackTarget(scenarioId, revision, t))}
        />
      </Paper>
      {/* The History block of the catalogue goes here. */}
      {target && <ScenarioUpdateDialog target={target} onClose={() => setTarget(null)} onApplied={applied} />}
    </div>
  );
};

export default ScenarioVersions;
