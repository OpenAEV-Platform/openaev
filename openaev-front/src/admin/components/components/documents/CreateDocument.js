import { Add, ControlPointOutlined } from '@mui/icons-material';
import { Fab, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { useContext, useState } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addDocument, fetchDocument } from '../../../../actions/Document';
import Dialog from '../../../../components/common/dialog/Dialog.tsx';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import { DocumentContext } from '../../common/Context';
import DocumentForm from './DocumentForm';

const styles = theme => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 30,
  },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
});

const CreateDocument = (props) => {
  const { classes, t, inline, filters } = props;
  const [open, setOpen] = useState(false);

  // Context
  const context = useContext(DocumentContext);
  const initialValues = context
    ? context.onInitDocument()
    // TODO: should be platform
    : {
        document_tags: [],
        document_exercises: [],
        document_scenarios: [],
      };
  const computeInputValues = data => R.pipe(
    R.assoc('document_tags', R.pluck('id', data.document_tags)),
    R.assoc('document_exercises', R.pluck('id', data.document_exercises)),
    R.assoc('document_scenarios', R.pluck('id', data.document_scenarios)),
  )(data);

  const onSubmit = (data) => {
    const inputValues = computeInputValues(data);
    const formData = new FormData();
    formData.append('file', data.document_file[0]);
    const blob = new Blob([JSON.stringify(inputValues)], { type: 'application/json' });
    formData.append('input', blob);
    return props.addDocument(formData).then((result) => {
      if (result.result) {
        if (props.onCreate) {
          const created = result.entities.documents[result.result];
          props.onCreate(created);
        }
        return setOpen(false);
      }
      return result;
    });
  };
  return (
    <>
      {inline === true ? (
        <ListItemButton divider onClick={() => setOpen(true)} color="primary">
          <ListItemIcon color="primary">
            <ControlPointOutlined color="primary" />
          </ListItemIcon>
          <ListItemText
            primary={t('Create a new document')}
            classes={{ primary: classes.text }}
          />
        </ListItemButton>
      ) : (
        <Fab
          onClick={() => setOpen(true)}
          color="primary"
          aria-label="Add"
          className={classes.createButton}
        >
          <Add />
        </Fab>
      )}
      {inline ? (
        <Dialog
          open={open}
          handleClose={() => setOpen(false)}
          title={t('Create a new document')}
        >
          <DocumentForm
            initialValues={initialValues}
            onSubmit={onSubmit}
            handleClose={() => setOpen(false)}
            filters={filters}
          />
        </Dialog>
      ) : (
        <Drawer
          open={open}
          handleClose={() => setOpen(false)}
          title={t('Create a new document')}
        >
          <DocumentForm
            initialValues={initialValues}
            onSubmit={onSubmit}
            handleClose={() => setOpen(false)}
            filters={filters}
          />
        </Drawer>
      )}
    </>
  );
};

CreateDocument.propTypes = {
  classes: PropTypes.object,
  t: PropTypes.func,
  addDocument: PropTypes.func,
  fetchDocument: PropTypes.func,
  inline: PropTypes.bool,
  filters: PropTypes.array,
};

export default R.compose(
  connect(null, {
    addDocument,
    fetchDocument,
  }),
  inject18n,
  Component => withStyles(Component, styles),
)(CreateDocument);
