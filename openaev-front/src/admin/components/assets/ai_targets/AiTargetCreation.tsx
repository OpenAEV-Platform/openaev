import { type FunctionComponent, useState } from 'react';

import { addAiTarget } from '../../../../actions/assets/aiTarget-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type AiTarget, type AiTargetInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import AiTargetForm from './AiTargetForm';

interface Props { onCreate: (result: AiTarget) => void }

const AiTargetCreation: FunctionComponent<Props> = ({ onCreate }) => {
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();

  const dispatch = useAppDispatch();
  const onSubmit = (data: AiTargetInput) => {
    dispatch(addAiTarget(data)).then(
      (result: {
        result: string;
        entities: { aitargets: Record<string, AiTarget> };
      }) => {
        if (result.entities) {
          if (onCreate) {
            const aiTargetCreated = result.entities.aitargets[result.result];
            onCreate(aiTargetCreated);
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
        title={t('Create a new AI target')}
      >
        <AiTargetForm
          onSubmit={onSubmit}
          handleClose={() => setOpen(false)}
        />
      </Drawer>
    </>
  );
};

export default AiTargetCreation;
