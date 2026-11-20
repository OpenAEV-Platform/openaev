import { Button, Paper, Text } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode, useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { fetchScenario } from '../../../../../actions/scenarios/scenario-actions';
import { fetchScenarioNewerVersions, fetchScenarioRevisions } from '../../../../../actions/scenarios/scenario-update-actions';
import { useFormatter } from '../../../../../components/i18n';
import { type CatalogScenarioVersionOutput, type ScenarioRevisionOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useScenarioPermissions from '../../../../../utils/permissions/useScenarioPermissions';
import ScenarioUpdateDialog from './ScenarioUpdateDialog';
import { rollbackTarget, type ScenarioUpdateTarget, updateTarget } from './scenarioUpdateTargets';

interface Props { installedVersion: string }

const REASONS: Record<ScenarioRevisionOutput['scenario_revision_reason'], string> = {
  UPDATE: 'Before an update',
  ROLLBACK: 'Before a rollback',
};

const Row = ({ children, action }: {
  children: ReactNode;
  action: ReactNode;
}) => (
  <div style={{
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
  }}
  >
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 4,
      minWidth: 0,
    }}
    >
      {children}
    </div>
    {action}
  </div>
);

// The installed catalogue version, the newer ones to update to, and the content kept to roll back to.
const ScenarioVersions: FunctionComponent<Props> = ({ installedVersion }) => {
  const { t, nsd, nsdt } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: string };
  const dispatch = useAppDispatch();
  const { canManage } = useScenarioPermissions(scenarioId);
  const [versions, setVersions] = useState<CatalogScenarioVersionOutput[]>([]);
  const [revisions, setRevisions] = useState<ScenarioRevisionOutput[]>([]);
  const [target, setTarget] = useState<ScenarioUpdateTarget | null>(null);

  const load = useCallback(() => {
    fetchScenarioNewerVersions(scenarioId).then(setVersions);
    fetchScenarioRevisions(scenarioId).then(setRevisions);
  }, [scenarioId]);
  useEffect(load, [load]);

  const applied = () => {
    setTarget(null);
    dispatch(fetchScenario(scenarioId));
    load();
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 24,
    }}
    >
      <Paper title={t('Installed version: v{version}', { version: installedVersion })} padding={16}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          {versions.length === 0 && <Text variant="content-base">{t('No newer version in the catalogue')}</Text>}
          {versions.map(version => (
            <Row
              key={version.catalog_scenario_version}
              action={canManage && (
                <Button size="sm" priority="secondary" onClick={() => setTarget(updateTarget(scenarioId, version.catalog_scenario_version, t))}>
                  {t('Update')}
                </Button>
              )}
            >
              <Text variant="content-base-medium">
                {version.catalog_scenario_published_at
                  ? `v${version.catalog_scenario_version} · ${nsd(version.catalog_scenario_published_at)}`
                  : `v${version.catalog_scenario_version}`}
              </Text>
              {version.catalog_scenario_changelog && <Text variant="content-caption">{version.catalog_scenario_changelog}</Text>}
            </Row>
          ))}
        </div>
      </Paper>
      <Paper title={t('Revisions')} padding={16}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          {revisions.length === 0 && <Text variant="content-base">{t('No revision yet')}</Text>}
          {revisions.map((revision) => {
            const date = nsdt(revision.scenario_revision_created_at);
            return (
              <Row
                key={revision.scenario_revision_id}
                action={canManage && (
                  <Button size="sm" priority="secondary" onClick={() => setTarget(rollbackTarget(scenarioId, revision.scenario_revision_id, date, t))}>
                    {t('Roll back')}
                  </Button>
                )}
              >
                <Text variant="content-base-medium">{`${t(REASONS[revision.scenario_revision_reason])} · v${revision.scenario_revision_catalog_version}`}</Text>
                <Text variant="content-caption">{revision.scenario_revision_user ? `${date} · ${revision.scenario_revision_user}` : date}</Text>
              </Row>
            );
          })}
        </div>
      </Paper>
      {target && <ScenarioUpdateDialog target={target} onClose={() => setTarget(null)} onApplied={applied} />}
    </div>
  );
};

export default ScenarioVersions;
