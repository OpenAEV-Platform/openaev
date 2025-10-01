import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { addCve } from '../../../../actions/cve-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type CveCreateInput, type CveSimple } from '../../../../utils/api-types';
import CveForm from './CveForm';

const useStyles = makeStyles()({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 230,
  },
});

interface Props { onCreate?: (result: CveSimple) => void }

const CreateCve: FunctionComponent<Props> = ({ onCreate }) => {
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const { classes } = useStyles();

  const onSubmit = (data: CveCreateInput) => {
    addCve(data).then(
      (result: { data: CveSimple }) => {
        if (result) {
          if (onCreate) {
            onCreate(result.data);
          }
          setOpen(false);
        }
        return result;
      },
    );
  };

  const handleClose = () => {
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
        title={t('Add a new vulnerability')}
      >
        <CveForm
          onSubmit={onSubmit}
          handleClose={handleClose}
        />
      </Drawer>
    </>
  );
};

export default CreateCve;
