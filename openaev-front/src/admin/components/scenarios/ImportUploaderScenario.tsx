import { useNavigate } from 'react-router';

import { importScenario } from '../../../actions/scenarios/scenario-actions';
import ImportUploader from '../../../components/common/ImportUploader';

const ImportUploaderScenario = () => {
  // Standard hooks
  const navigate = useNavigate();

  const handleUpload = async (formData: FormData) => {
    await importScenario(formData).then(() => {
      navigate(0);
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
