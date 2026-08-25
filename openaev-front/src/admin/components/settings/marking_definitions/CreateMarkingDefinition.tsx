import { type FunctionComponent, useState } from 'react';

import { addMarkingDefinition } from '../../../../actions/markings/marking-definition-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type MarkingDefinitionInput, type MarkingDefinitionOutput } from '../../../../utils/api-types';
import MarkingDefinitionForm from './MarkingDefinitionForm';

interface Props { onCreate?: (marking: MarkingDefinitionOutput) => void }

const CreateMarkingDefinition: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const [open, setOpen] = useState(false);

  const onSubmit = (data: MarkingDefinitionInput) => {
    return addMarkingDefinition(data).then((result: { data: MarkingDefinitionOutput }) => {
      onCreate?.(result.data);
      setOpen(false);
      return result;
    });
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a marking definition')}
      >
        <MarkingDefinitionForm
          onSubmit={onSubmit}
          onCancel={() => setOpen(false)}
        />
      </Drawer>
    </>
  );
};

export default CreateMarkingDefinition;
