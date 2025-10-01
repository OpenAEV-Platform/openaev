import { useNavigate } from 'react-router';

import { importEndpoints } from '../../../../actions/assets/endpoint-actions';
import ImportUploader from '../../../../components/common/ImportUploader';
import { useFormatter } from '../../../../components/i18n';

const ImportUploaderEndpoints = () => {
  const navigate = useNavigate();
  const { t } = useFormatter();

  const handleUpload = (file: FormData) => {
    return importEndpoints(file, 'ENDPOINTS').then((result) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        navigate(0);
      }
    });
  };

  return (
    <ImportUploader title={t('Import endpoints')} handleUpload={handleUpload} />
  );
};

export default ImportUploaderEndpoints;
