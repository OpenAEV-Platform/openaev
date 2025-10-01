import { useNavigate } from 'react-router';

import { importScenario } from '../../../actions/scenarios/scenario-actions';
import ImportUploader from '../../../components/common/ImportUploader';
import { useAppDispatch } from '../../../utils/hooks';

const ImportUploaderScenario = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const handleUpload = async (formData: FormData) => {
    await dispatch(importScenario(formData)).then((result: { [x: string]: string }) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        navigate(0);
      }
    });
  };

  return (
    <ImportUploader
      title="Import a scenario"
      handleUpload={handleUpload}
    />
  );
};

export default ImportUploaderScenario;
