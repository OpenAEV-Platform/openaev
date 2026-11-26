import { type FunctionComponent, useState } from 'react';

import { addThreatArsenalAction } from '../../../actions/threat_arsenals/threatArsenal-actions';
import ButtonCreate from '../../../components/common/ButtonCreate';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import {
  type ThreatArsenalAction,
  type ThreatArsenalActionCreateInput as ApiThreatArsenalActionCreateInput,
} from '../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../utils/api-types-custom';
import ThreatArsenalActionForm from './ThreatArsenalActionForm';
import { toActionInput } from './utils/actionFormValues';

interface Props { onCreate?: (action: ThreatArsenalAction) => void }

const CreateThreatArsenalAction: FunctionComponent<Props> = ({ onCreate }) => {
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();

  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const onSubmit = (data: ThreatArsenalActionCreateCustomInput) => {
    const inputValues: ApiThreatArsenalActionCreateInput = {
      ...toActionInput(data),
      action_source: 'MANUAL',
      action_status: 'VERIFIED',
    };

    return addThreatArsenalAction(inputValues).then(({ data }: { data: ThreatArsenalAction }) => {
      if (data && onCreate) {
        onCreate(data);
      }
      setOpen(false);
    });
  };

  return (
    <>
      <ButtonCreate onClick={handleOpen} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new action')}
      >
        <ThreatArsenalActionForm
          editing={false}
          onSubmit={onSubmit}
          handleClose={handleClose}
        />
      </Drawer>
    </>
  );
};

export default CreateThreatArsenalAction;
