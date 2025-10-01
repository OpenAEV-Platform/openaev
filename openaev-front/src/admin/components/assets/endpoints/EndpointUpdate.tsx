import { type FunctionComponent, useState } from 'react';

import type { EndpointHelper } from '../../../../actions/assets/asset-helper';
import { fetchEndpoint, updateEndpoint } from '../../../../actions/assets/endpoint-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { useHelper } from '../../../../store';
import type { Endpoint, EndpointInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import EndpointForm from './EndpointForm';

interface Props {
  open: boolean;
  handleClose: () => void;
  agentless?: boolean;
  onUpdate?: (result: Endpoint) => void;
  endpointId: string;
}

const EndpointUpdate: FunctionComponent<Props> = ({
  open,
  handleClose,
  agentless,
  onUpdate,
  endpointId,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const [loading, setLoading] = useState(true);
  const dispatch = useAppDispatch();

  const { endpoint } = useHelper((helper: EndpointHelper) => ({ endpoint: helper.getEndpoint(endpointId) }));
  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchEndpoint(endpointId)).finally(() => setLoading(false));
  });

  const onSubmit = (data: EndpointInput) => {
    dispatch(updateEndpoint(endpointId, data)).then(
      (result: {
        result: string;
        entities: { endpoints: Record<string, Endpoint> };
      }) => {
        if (result.entities) {
          if (onUpdate) {
            const endpointUpdated = result.entities.endpoints[result.result];
            onUpdate(endpointUpdated);
          }
          handleClose();
        }
        return result;
      },
    );
  };

  if (loading) {
    return <Loader />;
  }
  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Update an endpoint')}
    >
      <EndpointForm
        initialValues={endpoint}
        editing
        onSubmit={onSubmit}
        agentless={agentless}
        handleClose={handleClose}
      />
    </Drawer>
  );
};

export default EndpointUpdate;
