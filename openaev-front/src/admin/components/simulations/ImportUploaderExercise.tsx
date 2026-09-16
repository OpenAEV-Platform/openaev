import { importingExercise } from '../../../actions/Exercise';
import ImportUploader from '../../../components/common/ImportUploader';
import { useFormatter } from '../../../components/i18n';
import { type ImportResult } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import { notifyPartialImport } from '../../../utils/importResultNotifier';

const ImportUploaderExercise = ({ refresh }: { refresh: () => void }) => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const { t } = useFormatter();

  const handleUpload = async (formData: FormData) => {
    await dispatch(importingExercise(formData)).then((result: ImportResult) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        // A partial import shows a sticky toast; skip the full page reload that would discard it.
        notifyPartialImport(result, t);
        refresh();
      }
    });
  };

  return (
    <ImportUploader title="Import a simulation" handleUpload={handleUpload} />
  );
};

export default ImportUploaderExercise;
