import { Button, Paper } from '@filigran/design-system';
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { fetchCatalogScenarioVersions, fetchScenarioRevisions, type ScenarioRevision } from '../../../../../actions/catalog/scenario-catalog-actions';
import { fetchScenario } from '../../../../../actions/scenarios/scenario-actions';
import { ContentHistoryTable, ContentVersionsTable } from '../../../../../components/common/content_versions/ContentVersionsTable';
import { useFormatter } from '../../../../../components/i18n';
import { type CatalogScenarioVersionsOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { useAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import ScenarioUpdateDialog from './ScenarioUpdateDialog';

const REASON_LABELS: Record<ScenarioRevision['scenario_revision_reason'], string> = {
  INSTALL: 'Installed',
  UPDATE: 'Before update',
  ROLLBACK: 'Before rollback',
  PAYLOAD_VERSION_CHANGE: 'Before payload version change',
};

// F7: the releases, one installed instance updated in place, its revisions, then the history.
const ScenarioVersions: FunctionComponent = () => {
  const { t, nsdt } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: string };
  const dispatch = useAppDispatch();
  const canManage = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const [data, setData] = useState<CatalogScenarioVersionsOutput | null>(null);
  const [revisions, setRevisions] = useState<ScenarioRevision[]>([]);
  const [target, setTarget] = useState<{
    version?: string;
    revision?: ScenarioRevision;
  } | null>(null);

  const load = () => {
    fetchCatalogScenarioVersions(scenarioId).then(response => setData(response.data));
    fetchScenarioRevisions(scenarioId).then(response => setRevisions(response.data));
  };
  useEffect(load, [scenarioId]);

  // The releases come newest first: those above the current one are updates.
  const versions = data?.versions ?? [];
  const currentIndex = versions.findIndex(version => version.is_current);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 24,
    }}
    >
      <Paper title={t('Versions')} padding={16}>
        <ContentVersionsTable
          usageLabel={t('Simulations')}
          rows={versions.map((version, index) => ({
            version: version.version,
            current: version.is_current,
            latest: version.is_latest,
            installed: version.is_current,
            revoked: version.status === 'REVOKED',
            changelog: version.changelog,
            publishedAt: version.published_at,
            usage: version.is_current ? String(version.simulation_count) : undefined,
            action: index < currentIndex && version.status !== 'REVOKED'
              ? (
                  <Button size="sm" priority="secondary" disabled={!canManage} onClick={() => setTarget({ version: version.version })}>
                    {t('Update')}
                  </Button>
                )
              : undefined,
          }))}
        />
      </Paper>
      <Paper title={t('Revisions')} padding={16}>
        <Table size="small" aria-label={t('Revisions')}>
          <TableHead>
            <TableRow>
              <TableCell>{t('Revision')}</TableCell>
              <TableCell>{t('Reason')}</TableCell>
              <TableCell>{t('Version')}</TableCell>
              <TableCell>{t('User')}</TableCell>
              <TableCell>{t('Date')}</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {revisions.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>{t('No revision yet')}</TableCell>
              </TableRow>
            )}
            {revisions.map(revision => (
              <TableRow key={revision.scenario_revision_id}>
                <TableCell>{`#${revision.scenario_revision_number}`}</TableCell>
                <TableCell>{t(REASON_LABELS[revision.scenario_revision_reason])}</TableCell>
                <TableCell>{revision.scenario_revision_catalog_version ? `v${revision.scenario_revision_catalog_version}` : '-'}</TableCell>
                <TableCell>{revision.scenario_revision_author ?? '-'}</TableCell>
                <TableCell>{nsdt(revision.scenario_revision_created_at)}</TableCell>
                <TableCell align="right">
                  <Button size="sm" priority="secondary" disabled={!canManage} onClick={() => setTarget({ revision })}>
                    {t('Roll back to this revision')}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      <Paper title={t('History')} padding={16}>
        <ContentHistoryTable history={data?.history ?? []} />
      </Paper>
      {target && (
        <ScenarioUpdateDialog
          scenarioId={scenarioId}
          version={target.version}
          revisionId={target.revision?.scenario_revision_id}
          revisionNumber={target.revision?.scenario_revision_number}
          onClose={() => setTarget(null)}
          onApplied={() => {
            setTarget(null);
            dispatch(fetchScenario(scenarioId));
            load();
          }}
        />
      )}
    </div>
  );
};

export default ScenarioVersions;
