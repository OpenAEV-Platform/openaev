import Dialog from '../../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../../components/i18n';
import ImportFileSelector from './ImportFileSelector';

interface ImportJsonDialogProps {
  open: boolean;
  handleClose: () => void;
  handleSubmit: (values: { file: File }) => void;
}
const InjectImportJsonDialog = (props: ImportJsonDialogProps) => {
  const { t } = useFormatter();

  return (
    <Dialog
      open={props.open}
      handleClose={props.handleClose}
      title={t('Import injects')}
      maxWidth="sm"
    >
      <ImportFileSelector
        label={t('inject_import_file_must_be_zip')}
        mimeTypes="application/octet-stream, multipart/x-zip, application/zip, application/zip-compressed, application/x-zip-compressed"
        submitActionLabel={t('Import')}
        handleClose={props.handleClose}
        handleSubmit={props.handleSubmit}
      />
    </Dialog>
  );
};

export default InjectImportJsonDialog;
