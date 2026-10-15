import { Button, Chip, Paper } from '@filigran/design-system';
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { fetchCatalogScenarioVersions } from '../../../../../actions/catalog/scenario-catalog-actions';
import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type CatalogScenarioVersionsOutput, type ContentHistoryOutput } from '../../../../../utils/api-types';
import { useAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import CatalogScenarioInstallDrawer from '../../catalog/CatalogScenarioInstallDrawer';

const HISTORY_ACTION_LABELS: Record<string, string> = {
  INSTALL: 'Installed',
  UNINSTALL: 'Uninstalled',
};

// F7: every release of the catalogue scenario, which ones are installed, then the history.
const ScenarioVersions: FunctionComponent = () => {
  const { t, nsdt } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: string };
  const navigate = useNavigate();
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  const [data, setData] = useState<CatalogScenarioVersionsOutput | null>(null);
  const [installing, setInstalling] = useState<string | null>(null);

  useEffect(() => {
    fetchCatalogScenarioVersions(scenarioId).then(response => setData(response.data));
  }, [scenarioId]);

  const historyLabel = (entry: ContentHistoryOutput) => {
    const label = t(HISTORY_ACTION_LABELS[entry.content_history_action ?? ''] ?? entry.content_history_action ?? '-');
    return `${label} v${entry.content_history_version ?? '-'}`;
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 24,
    }}
    >
      <Paper title={t('Versions')} padding={16}>
        <Table size="small" aria-label={t('Versions')}>
          <TableHead>
            <TableRow>
              <TableCell>{t('Version')}</TableCell>
              <TableCell>{t('Status')}</TableCell>
              <TableCell>{t('Changelog')}</TableCell>
              <TableCell>{t('Published')}</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {(data?.versions ?? []).map(version => (
              <TableRow key={version.version}>
                <TableCell>
                  {version.scenario_id && !version.is_current
                    ? <Link to={`/admin/scenarios/${version.scenario_id}`}>{`v${version.version}`}</Link>
                    : `v${version.version}`}
                </TableCell>
                <TableCell>
                  <div style={{
                    display: 'flex',
                    gap: 4,
                    flexWrap: 'wrap',
                  }}
                  >
                    {version.is_current && <Chip label={t('Current version')} />}
                    {version.is_latest && <Chip label={t('Latest version')} severity="info" />}
                    {version.scenario_id && !version.is_current && <Chip label={t('Installed')} severity="low" />}
                    {version.status === 'REVOKED' && <Chip label={t('Revoked')} severity="critical" />}
                  </div>
                </TableCell>
                <TableCell>{version.changelog ?? '-'}</TableCell>
                <TableCell>{version.published_at ? nsdt(version.published_at) : '-'}</TableCell>
                <TableCell align="right">
                  {!version.scenario_id && version.status !== 'REVOKED' && (
                    <Button size="sm" priority="secondary" disabled={!canInstall} onClick={() => setInstalling(version.version)}>
                      {t('Install')}
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      <Paper title={t('History')} padding={16}>
        <Table size="small" aria-label={t('History')}>
          <TableHead>
            <TableRow>
              <TableCell>{t('History')}</TableCell>
              <TableCell>{t('User')}</TableCell>
              <TableCell>{t('Date')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(data?.history ?? []).length === 0 && (
              <TableRow>
                <TableCell colSpan={3}>{t('No history yet')}</TableCell>
              </TableRow>
            )}
            {(data?.history ?? []).map(entry => (
              <TableRow key={entry.content_history_id}>
                <TableCell>{historyLabel(entry)}</TableCell>
                <TableCell>{entry.content_history_user ?? '-'}</TableCell>
                <TableCell>{entry.content_history_created_at ? nsdt(entry.content_history_created_at) : '-'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      {installing && data && (
        <CatalogScenarioInstallDrawer
          externalId={data.catalog_id}
          name={scenario?.scenario_name ?? data.catalog_id}
          version={installing}
          onClose={() => setInstalling(null)}
          onInstalled={id => navigate(`/admin/scenarios/${id}`)}
        />
      )}
    </div>
  );
};

export default ScenarioVersions;
