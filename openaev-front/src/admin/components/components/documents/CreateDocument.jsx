import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { useContext, useState } from 'react';
import { connect } from 'react-redux';

import { addDocument, fetchDocument } from '../../../../actions/Document';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Dialog from '../../../../components/common/dialog/Dialog.tsx';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import { DocumentContext } from '../../common/Context';
import DocumentForm from './DocumentForm';

const CreateDocumentComponent = (props) => {
  const { t, inline, filters } = props;
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
        // Header placement (picker top-right): compact creation button.
        <ButtonCreate onClick={() => setOpen(true)} label={t('Create a new document')} />
      ) : (
        <ButtonCreate onClick={() => setOpen(true)} />
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

CreateDocumentComponent.propTypes = {
  t: PropTypes.func,
  addDocument: PropTypes.func,
  fetchDocument: PropTypes.func,
  inline: PropTypes.bool,
  filters: PropTypes.array,
};

const CreateDocument = R.compose(
  connect(null, {
    addDocument,
    fetchDocument,
  }),
  inject18n,
)(CreateDocumentComponent);

export default CreateDocument;
