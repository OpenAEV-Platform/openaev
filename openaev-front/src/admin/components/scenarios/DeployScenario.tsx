import { useNavigate, useParams } from 'react-router';

import { importScenario } from '../../../actions/scenarios/scenario-actions';
import Loader from '../../../components/Loader';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useXtmHubDownloadDocument from '../../../utils/hooks/useXtmHubDownloadDocument';
import XtmHubDialogConnectivityLost from '../xtm_hub/dialog/connectivity-lost';

const DeployScenario: React.FC = async () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { serviceInstanceId, fileId } = useParams();
  const sendImportToBack = async (importedFile: File) => {
    const formData = new FormData();
    formData.append('file', importedFile);
    await dispatch(importScenario(formData)).then(() => {
      navigate('/admin/scenarios');
    });
  };
  const onDownloadError = () => {
    navigate('/admin/scenarios');
    MESSAGING$.notifyError('An error occurred while importing scenario. You have been redirected to home page.');
  };
  const { dialogConnectivityLostStatus } = useXtmHubDownloadDocument({
    serviceInstanceId,
    fileId,
    onSuccess: sendImportToBack,
    onError: onDownloadError,
  });

  const onConfirm = () => {
    navigate('/admin/settings/experience');
  };

  const onCancel = () => {
    navigate('/admin/scenarios');
  };

  return (
    <>
      <XtmHubDialogConnectivityLost
        status={dialogConnectivityLostStatus}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />
      <Loader />
    </>
  );
};

export default DeployScenario;
