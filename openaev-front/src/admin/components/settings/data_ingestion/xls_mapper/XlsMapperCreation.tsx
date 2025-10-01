import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { createMapper } from '../../../../../actions/mapper/mapper-actions';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type ImportMapperAddInput, type RawPaginationImportMapper } from '../../../../../utils/api-types';
import MapperForm from './MapperForm';

const useStyles = makeStyles()(() => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 230,
  },
}));

interface Props { onCreate?: (result: RawPaginationImportMapper) => void }

const XlsMapperCreation: FunctionComponent<Props> = ({ onCreate }) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  const [open, setOpen] = useState(false);

  const onSubmit = (data: ImportMapperAddInput) => {
    createMapper(data).then(
      (result: { data: RawPaginationImportMapper }) => {
        onCreate?.(result.data);
        return result;
      },
    );
    setOpen(false);
  };

  return (
    <>
      <Fab
        onClick={() => setOpen(true)}
        color="primary"
        aria-label="Add"
        className={classes.createButton}
      >
        <Add />
      </Fab>
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
