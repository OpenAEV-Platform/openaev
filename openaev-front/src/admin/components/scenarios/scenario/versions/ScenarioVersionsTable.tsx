import { Button, Chip, type ChipSeverity } from '@filigran/design-system';
// fds:keep-mui the library has no Table yet; dressed to its spec by FDS_TABLE_SX
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { FDS_TABLE_SX } from '../../../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../../../components/i18n';
import { type ScenarioVersionOutput, type ScenarioVersionsOutput } from '../../../../../utils/api-types';

const NOT_OFFERED: Partial<Record<ScenarioVersionOutput['scenario_version_status'], {
  label: string;
  severity: ChipSeverity;
}>> = {
  DEPRECATED: {
    label: 'Deprecated',
    severity: 'medium',
  },
  REVOKED: {
    label: 'Revoked',
    severity: 'critical',
  },
};

interface Props {
  versions: ScenarioVersionsOutput;
  canManage: boolean;
  onUpdate: (version: string) => void;
}

// Every catalogue version, newest first: an offered one above the current one can be updated to.
const ScenarioVersionsTable: FunctionComponent<Props> = ({ versions, canManage, onUpdate }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const rows = versions.scenario_versions;
  const currentIndex = rows.findIndex(version => version.scenario_version_current);

  return (
    <Table size="small" aria-label={t('Versions')} sx={FDS_TABLE_SX}>
      <TableHead>
        <TableRow>
          <TableCell>{t('Version')}</TableCell>
          <TableCell>{t('Status')}</TableCell>
          <TableCell>{t('Changelog')}</TableCell>
          <TableCell>{t('Published')}</TableCell>
          <TableCell>{t('Simulations')}</TableCell>
          <TableCell />
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((version, index) => {
          const notOffered = NOT_OFFERED[version.scenario_version_status];
          return (
            <TableRow key={version.scenario_version}>
              <TableCell>{`v${version.scenario_version}`}</TableCell>
              <TableCell>
                <div style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: theme.spacing(0.5),
                }}
                >
                  {version.scenario_version_current && <Chip label={t('Current version')} style={{ maxWidth: '100%' }} />}
                  {version.scenario_version_latest && <Chip label={t('Latest version')} severity="low" style={{ maxWidth: '100%' }} />}
                  {notOffered && <Chip label={t(notOffered.label)} severity={notOffered.severity} style={{ maxWidth: '100%' }} />}
                </div>
              </TableCell>
              <TableCell>{version.scenario_version_changelog ?? '-'}</TableCell>
              <TableCell>{version.scenario_version_published_at ? nsdt(version.scenario_version_published_at) : '-'}</TableCell>
              <TableCell>{version.scenario_version_current ? versions.scenario_simulations_count : '-'}</TableCell>
              <TableCell align="right">
                {index < currentIndex && !notOffered && (
                  <Button size="sm" priority="secondary" disabled={!canManage} onClick={() => onUpdate(version.scenario_version)}>
                    {t('Update')}
                  </Button>
                )}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
};

export default ScenarioVersionsTable;
