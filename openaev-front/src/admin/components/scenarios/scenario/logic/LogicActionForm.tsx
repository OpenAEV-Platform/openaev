import { type FunctionComponent, useContext } from 'react';
import { useParams } from 'react-router';

import { type InjectOutputType } from '../../../../../actions/injects/Inject';
import { type Scenario, type InjectInput, type AtomicTestingInput } from '../../../../../utils/api-types';
import { useFormatter } from '../../../../../components/i18n';
import { InjectContext } from '../../../common/Context';
import CreateInject from '../../../common/injects/CreateInject';

interface Props {
  open: boolean;
  handleClose: () => void;
  onContractSelected: (data: InjectInput | AtomicTestingInput) => Promise<void>;
}

const LogicActionForm: FunctionComponent<Props> = ({
  open,
  handleClose,
  onContractSelected,
}) => {
  const { t } = useFormatter();

  return (
    <CreateInject
      title={t('Select an action')}
      onCreateInject={onContractSelected}
      open={open}
      handleClose={handleClose}
      isAtomic={false}
    />
  );
};

export default LogicActionForm;
