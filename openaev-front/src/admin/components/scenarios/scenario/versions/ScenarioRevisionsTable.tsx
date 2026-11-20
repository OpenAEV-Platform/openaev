import { Button } from '@filigran/design-system';
// fds:keep-mui the library has no Table yet; dressed to its spec by FDS_TABLE_SX
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { type FunctionComponent } from 'react';

import { FDS_TABLE_SX } from '../../../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../../../components/i18n';
import { type ScenarioRevisionOutput } from '../../../../../utils/api-types';

const REASONS: Record<ScenarioRevisionOutput['scenario_revision_reason'], string> = {
  INSTALL: 'Installed',
  UPDATE: 'Before an update',
  ROLLBACK: 'Before a rollback',
};

interface Props {
  revisions: ScenarioRevisionOutput[];
  canManage: boolean;
  onRollBack: (revision: ScenarioRevisionOutput) => void;
}

// The content kept at install and before each update or rollback, newest first, to roll back to.
const ScenarioRevisionsTable: FunctionComponent<Props> = ({ revisions, canManage, onRollBack }) => {
  const { t, nsdt } = useFormatter();
  return (
    <Table size="small" aria-label={t('Revisions')} sx={FDS_TABLE_SX}>
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
            <TableCell>{t(REASONS[revision.scenario_revision_reason])}</TableCell>
            <TableCell>{`v${revision.scenario_revision_catalog_version}`}</TableCell>
            <TableCell>{revision.scenario_revision_user ?? '-'}</TableCell>
            <TableCell>{nsdt(revision.scenario_revision_created_at)}</TableCell>
            <TableCell align="right">
              <Button size="sm" priority="secondary" disabled={!canManage} onClick={() => onRollBack(revision)}>
                {t('Roll back to this revision')}
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
};

export default ScenarioRevisionsTable;
