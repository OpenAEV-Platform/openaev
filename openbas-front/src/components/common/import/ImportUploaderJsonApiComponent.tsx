import { useNavigate } from 'react-router';
import { type Dispatch } from 'redux';

import { useAppDispatch } from '../../../utils/hooks';
import ImportUploader from '../ImportUploader';

interface Props {
  title: string;
  uploadFn: (content: FormData) => (dispatch: Dispatch) => Promise<{ [x: string]: string }>;
}

const ImportUploaderJsonApiComponent = ({
  title,
  uploadFn,
}: Props) => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const handleUpload = async (_: FormData, file: File) => {
    const form = new FormData();
    form.append('file', file);

    await dispatch(uploadFn(form)).then((result: { [x: string]: string }) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        navigate(0);
      }
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
