import {
  Button,
  Checkbox,
  Dialog, DialogActions,
  DialogContent,
  DialogTitle,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../i18n';
import Transition from '../Transition';

interface ExportOptionsProps {
  title: string;
  open: boolean;
  onCancel: () => void;
  onClose: () => void;
  onSubmit: (withPlayer: boolean, withTeams: boolean, withVariableValues: boolean) => void;
}

const ExportOptionsDialog: FunctionComponent<ExportOptionsProps> = ({
  title,
  open,
  onCancel,
  onClose,
  onSubmit,
}) => {
  const { t } = useFormatter();

  const [exportTeams, setExportTeams] = useState(false);
  const handleToggleExportTeams = () => setExportTeams(!exportTeams);

  const [exportPlayers, setExportPlayers] = useState(false);
  const handleToggleExportPlayers = () => setExportPlayers(!exportPlayers);

  const [exportVariableValues, setExportVariableValues] = useState(false);
  const handleToggleExportVariableValues = () => setExportVariableValues(!exportVariableValues);

  const doSubmit = () => {
    onSubmit(exportPlayers, exportTeams, exportVariableValues);
  };

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={onClose}
      slotProps={{ paper: { elevation: 1 } }}
    >
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <TableContainer>
          <Table aria-label="export table" size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('Elements')}</TableCell>
                <TableCell style={{ textAlign: 'center' }}>
                  {t('Export')}
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              <TableRow>
                <TableCell>
                  {t('Injects (including attached files)')}
                </TableCell>
                <TableCell style={{ textAlign: 'center' }}>
                  <Checkbox checked={true} disabled={true} />
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>{t('Teams')}</TableCell>
                <TableCell style={{ textAlign: 'center' }}>
                  <Checkbox
                    checked={exportTeams}
                    onChange={handleToggleExportTeams}
                  />
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>{t('Players')}</TableCell>
                <TableCell style={{ textAlign: 'center' }}>
                  <Checkbox
                    checked={exportPlayers}
                    onChange={handleToggleExportPlayers}
                  />
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell>{t('Variable values')}</TableCell>
                <TableCell style={{ textAlign: 'center' }}>
                  <Checkbox
                    checked={exportVariableValues}
                    onChange={handleToggleExportVariableValues}
                  />
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </TableContainer>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>{t('Cancel')}</Button>
        <Button color="secondary" onClick={doSubmit}>
          {t('Export')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ExportOptionsDialog;
