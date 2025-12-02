import { type AxiosResponse } from 'axios';
import { useNavigate } from 'react-router';

import ImportUploader from '../ImportUploader';

interface Props {
  title: string;
  uploadFn: (content: FormData) => Promise<AxiosResponse>;
}

const ImportUploaderJsonApiComponent = ({
  title,
  uploadFn,
}: Props) => {
  // Standard hooks
  const navigate = useNavigate();

  const handleUpload = async (_: FormData, file: File) => {
    const form = new FormData();
    form.append('file', file);

    await uploadFn(form).then(() => {
      navigate(0);
    });
  };

  return (
    <ImportUploader
      title={title}
      handleUpload={handleUpload}
      fileAccepted=".zip"
    />
  );
};

export default ImportUploaderJsonApiComponent;
