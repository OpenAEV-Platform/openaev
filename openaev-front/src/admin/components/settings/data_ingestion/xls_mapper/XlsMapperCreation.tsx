import { type FunctionComponent, useState } from 'react';

import { createMapper } from '../../../../../actions/mapper/mapper-actions';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type ImportMapperAddInput, type ImportMapperOutput, type ImportMapperSimpleOutput } from '../../../../../utils/api-types';
import MapperForm from './MapperForm';

interface Props { onCreate?: (result: ImportMapperSimpleOutput) => void }

const XlsMapperCreation: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();

  const [open, setOpen] = useState(false);

  const onSubmit = (data: ImportMapperAddInput) => {
    createMapper(data).then(
      (result: { data: ImportMapperOutput }) => {
        onCreate?.(result.data);
        return result;
      },
    );
    setOpen(false);
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a xls mapper')}
      >
        <MapperForm onSubmit={onSubmit} />
      </Drawer>
    </>
  );
};

export default XlsMapperCreation;
