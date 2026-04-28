import { type FunctionComponent } from 'react';

import { type InjectInput, type AtomicTestingInput } from '../../../../../utils/api-types';
import { useFormatter } from '../../../../../components/i18n';
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
