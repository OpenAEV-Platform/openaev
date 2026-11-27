import { Button, Paper, Skeleton, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { useParams } from 'react-router';

import { fetchScenario } from '../../../../../actions/scenarios/scenario-actions';
import ContentHistoryTable from '../../../../../components/common/content_history/ContentHistoryTable';
import { useFormatter } from '../../../../../components/i18n';
import { useAppDispatch } from '../../../../../utils/hooks';
import useScenarioPermissions from '../../../../../utils/permissions/useScenarioPermissions';
import ScenarioRevisionsTable from './ScenarioRevisionsTable';
import ScenarioUpdateDialog from './ScenarioUpdateDialog';
import { rollbackTarget, type ScenarioUpdateTarget, updateTarget } from './scenarioUpdateTargets';
import ScenarioVersionsTable from './ScenarioVersionsTable';
import useScenarioVersions from './useScenarioVersions';

// The catalogue versions to update to, the content kept to roll back to, then what happened to the scenario.
const ScenarioVersions: FunctionComponent = () => {
  const { t, fsd } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: string };
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const { canManage } = useScenarioPermissions(scenarioId);
  const { versions, revisions, failed, history, reload } = useScenarioVersions(scenarioId);
  const [target, setTarget] = useState<ScenarioUpdateTarget | null>(null);
  const installedVersion = versions?.scenario_versions.find(version => version.scenario_version_current)?.scenario_version ?? '';

  const applied = () => {
    setTarget(null);
    dispatch(fetchScenario(scenarioId));
    reload();
  };

  if (failed) {
    return (
      <Paper title={t('Versions')} padding={16}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-start',
          gap: theme.spacing(1),
        }}
        >
          <Text variant="content-base">{t('Something went wrong. Please refresh the page or try again later.')}</Text>
          <Button priority="secondary" size="sm" onClick={reload}>{t('Try again')}</Button>
        </div>
      </Paper>
    );
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(3),
    }}
    >
      <Paper title={t('Versions')} padding={16}>
        {versions
          ? (
              <ScenarioVersionsTable
                versions={versions}
                canManage={canManage}
                onUpdate={version => setTarget(updateTarget(scenarioId, version, installedVersion, t))}
              />
            )
          : <Skeleton className="h-24" />}
      </Paper>
      <Paper title={t('Revisions')} padding={16}>
        {versions && revisions
          ? (
              <ScenarioRevisionsTable
                revisions={revisions}
                canManage={canManage}
                onRollBack={revision => setTarget(rollbackTarget(scenarioId, revision, installedVersion, t, fsd(revision.scenario_revision_created_at)))}
              />
            )
          : <Skeleton className="h-24" />}
      </Paper>
      <Paper title={t('History')} padding={16}>
        <ContentHistoryTable history={history} />
      </Paper>
      {target && <ScenarioUpdateDialog target={target} onClose={() => setTarget(null)} onApplied={applied} />}
    </div>
  );
};

export default ScenarioVersions;
