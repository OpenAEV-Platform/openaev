import { useNavigate } from 'react-router';

import { importMapper } from '../../../../actions/mapper/mapper-actions';
import ImportUploader from '../../../../components/common/ImportUploader';

const ImportUploaderMapper = () => {
  // Standard hooks
  const navigate = useNavigate();

  const handleUpload = async (formData: FormData) => {
    importMapper(formData).then((result: { data: { [x: string]: string } }) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        navigate(0);
      }
    });
  };

  return (
    <ImportUploader
      title="Import a mapper"
      handleUpload={handleUpload}
    />
  );
};

export default ImportUploaderMapper;
