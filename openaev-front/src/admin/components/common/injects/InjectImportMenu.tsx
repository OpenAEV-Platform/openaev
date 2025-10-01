import { CloudUploadOutlined } from '@mui/icons-material';
import { Menu, MenuItem, ToggleButton, Tooltip } from '@mui/material';
import { type MouseEvent as ReactMouseEvent, useContext, useState } from 'react';

import { storeXlsFile } from '../../../../actions/mapper/mapper-actions';
import Dialog from '../../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../../components/i18n';
import { type ImportMessage, type ImportPostSummary, type ImportTestSummary, type InjectsImportInput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { InjectContext } from '../Context';
import ImportFileSelector from './ImportFileSelector';
import ImportUploaderInjectFromXlsInjects from './ImportUploaderInjectFromXlsInjects';
import InjectImportJsonDialog from './InjectImportJsonDialog';

interface Props { onImportedInjects?: () => void }

const InjectImportMenu = ({ onImportedInjects = () => {} }: Props) => {
  // Standard hooks
  const { t } = useFormatter();
  const injectContext = useContext(InjectContext);

  const [importId, setImportId] = useState<string | undefined>(undefined);
  const [sheets, setSheets] = useState<string[]>([]);

  // Dialog
  const [openXlsImportDialog, setOpenXlsImportDialog] = useState(false);
  const [openJsonImportDialog, setOpenJsonImportDialog] = useState(false);
  const [menuOpen, setMenuOpen] = useState<{
    open: boolean;
    anchorEl: HTMLElement | null;
  }>({
    open: false,
    anchorEl: null,
  });

  const handleOpenMenu = (event: ReactMouseEvent<HTMLElement, MouseEvent>) => {
    event.preventDefault();
    setMenuOpen({
      open: true,
      anchorEl: event.currentTarget,
    });
  };
  const handleCloseMenu = () => {
    setMenuOpen({
      open: false,
      anchorEl: null,
    });
  };

  const handleXlsImportOpen = () => setOpenXlsImportDialog(true);
  const handleXlsImportClose = () => {
    setImportId(undefined);
    setSheets([]);
    setOpenXlsImportDialog(false);
    handleCloseMenu();
  };

  const handleJsonImportOpen = () => setOpenJsonImportDialog(true);
  const handleJsonImportClose = () => {
    setOpenJsonImportDialog(false);
    handleCloseMenu();
  };

  const onSubmitImportFile = (values: { file: File }) => {
    storeXlsFile(values.file).then((result: { data: ImportPostSummary }) => {
      const { data } = result;
      setImportId(data.import_id);
      setSheets(data.available_sheets);
    });
  };

  const onSubmitImportInjects = (input: InjectsImportInput) => {
    if (importId) {
      injectContext.onImportInjectFromXls?.(importId, input).then((value: ImportTestSummary) => {
        const criticalMessages = value.import_message?.filter((importMessage: ImportMessage) => importMessage.message_level === 'CRITICAL');
        if (criticalMessages && criticalMessages?.length > 0) {
          MESSAGING$.notifyError(t(criticalMessages[0].message_code || 'An unknown error occurred. Please contact your administrator or the OpenAEV maintainers.'), true);
        }
        onImportedInjects();
        handleXlsImportClose();
      });
    }
  };
  const onFileSelectSubmit = (values: { file: File }) => {
    injectContext.onImportInjectFromJson?.(values.file).then(() => {
      onImportedInjects();
      handleJsonImportClose();
    });
  };

  return (
    <>
      <ToggleButton
        value="import"
        aria-label="import"
        size="small"
        onClick={event => handleOpenMenu(event)}
      >
        <Tooltip
          title={t('Import injects')}
          aria-label="Import injects"
        >
          <CloudUploadOutlined
            color="primary"
            fontSize="small"
          />
        </Tooltip>
      </ToggleButton>
      <Menu
        id="menu-import-injects"
        anchorEl={menuOpen.anchorEl}
        open={menuOpen.open}
        onClose={handleCloseMenu}
      >
        <MenuItem onClick={handleJsonImportOpen}>{t('inject_import_json_action')}</MenuItem>
        <MenuItem onClick={handleXlsImportOpen}>{t('inject_import_xls_action')}</MenuItem>
      </Menu>
      <InjectImportJsonDialog open={openJsonImportDialog} handleClose={handleJsonImportClose} handleSubmit={onFileSelectSubmit} />
      <Dialog
        open={openXlsImportDialog}
        handleClose={handleXlsImportClose}
        title={t('Import injects')}
        maxWidth="sm"
      >
        <>
          {!importId
            && (
              <ImportFileSelector
                label={t('Your file should be a XLS')}
                mimeTypes="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/vnd.ms-excel"
                submitActionLabel={t('Next')}
                handleClose={handleXlsImportClose}
                handleSubmit={onSubmitImportFile}
              />
            )}
          {importId
            && (
              <ImportUploaderInjectFromXlsInjects
                sheets={sheets}
                handleClose={handleXlsImportClose}
                importId={importId}
                handleSubmit={onSubmitImportInjects}
              />
            )}
        </>
      </Dialog>
    </>
  );
};

export default InjectImportMenu;
