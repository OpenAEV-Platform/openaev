import { useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router';

import { importScenario } from '../../../actions/scenarios/scenario-actions';
import Loader from '../../../components/Loader';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import useXtmHubUserPlatformToken from '../../../utils/hooks/useXtmHubUserPlatformToken';

const DeployScenario: React.FC = () => {
  const { token } = useXtmHubUserPlatformToken();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { settings } = useAuth();
  const { serviceInstanceId, fileId } = useParams();
  const hasBeenCalled = useRef(false);
  useEffect(() => {
    if (!token) {
      return;
    }
    hasBeenCalled.current = true;
    const fetchData = async () => {
      try {
        const response = await fetch(
          `${settings.xtm_hub_enable && settings.xtm_hub_url ? settings.xtm_hub_url : 'https://hub.filigran.io'}/document/get/${serviceInstanceId}/${fileId}`,
          {
            method: 'GET',
            credentials: 'omit',
            headers: { 'XTM-Hub-User-Platform-Token': token },
          },
        );

        const blob = await response.blob();
        const file = new File([blob], 'downloaded.zip', { type: 'application/zip' });
        const formData = new FormData();
        formData.append('file', file);
        await dispatch(importScenario(formData)).then(() => {
          navigate('/admin/scenarios');
        });
      } catch {
        navigate('/admin');
        MESSAGING$.notifyError('An error occurred while importing scenario. You have been redirected to home page.');
      }
    };

    fetchData();
  }, [serviceInstanceId, fileId, token]);

  return (<Loader />);
};

export default DeployScenario;
