import { type FunctionComponent, useState } from 'react';

import { addEndpointAgentless } from '../../../../actions/assets/endpoint-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { Endpoint, EndpointInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import EndpointForm from './EndpointForm';

interface Props {
  agentless?: boolean;
  onCreate?: (result: Endpoint) => void;
}

const EndpointCreation: FunctionComponent<Props> = ({
  agentless,
  onCreate,
}) => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();

  const dispatch = useAppDispatch();
  const onSubmit = (data: EndpointInput) => {
    dispatch(addEndpointAgentless(data)).then(
      (result: {
        result: string;
        entities: { endpoints: Record<string, Endpoint> };
      }) => {
        if (result.entities) {
          if (onCreate) {
            const endpointCreated = result.entities.endpoints[result.result];
            onCreate(endpointCreated);
          }
          setOpen(false);
        }
        return result;
      },
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new endpoint')}
      >
        <EndpointForm
          onSubmit={onSubmit}
          agentless={agentless}
          handleClose={() => setOpen(false)}
        />
      </Drawer>
    </>
  );
};

export default EndpointCreation;
