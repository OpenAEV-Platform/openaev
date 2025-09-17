import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router';

import { importScenario } from '../../../actions/scenarios/scenario-actions';
import Loader from '../../../components/Loader';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';

const DeployScenario: React.FC = () => {
  // Get token
  // const { token } = useXtmHubUserPlatformToken();
  const navigate = useNavigate();
  const { serviceInstanceId, fileId } = useParams();
  const dispatch = useAppDispatch();

  useEffect(() => {
    // if (!token) {
    //   return;
    // }

    const fetchData = async () => {
      try {
        const response = await fetch(
          `http://localhost:3002/document/get/${serviceInstanceId}/${fileId}`,
          {
            method: 'GET',
            credentials: 'omit',
            // headers: {
              // 'XTM-Hub-User-Platform-Token': token,
            // },
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
  }, []);

  return (<Loader />);
};

export default DeployScenario;
