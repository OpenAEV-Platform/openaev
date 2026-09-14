import { type FunctionComponent, useContext, useState } from 'react';

import { addDocument } from '../../../../actions/Document';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Dialog from '../../../../components/common/dialog/Dialog';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type RawDocument } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { DocumentContext } from '../../common/Context';
import DocumentForm, { type DocumentFormInput } from './DocumentForm';

interface Props {
  onCreate?: (document: RawDocument) => void;
  inline?: boolean;
  filters?: string[];
}

const CreateDocument: FunctionComponent<Props> = ({ onCreate, inline = false, filters }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  // Context: pre-selects the current simulation or scenario, if any.
  const context = useContext(DocumentContext);
  const contextValues = context?.onInitDocument?.();
  const initialValues: Partial<DocumentFormInput> = {
    document_tags: (contextValues?.document_tags ?? []).map(option => option.id),
    document_exercises: (contextValues?.document_exercises ?? []).map(option => option.id),
    document_scenarios: (contextValues?.document_scenarios ?? []).map(option => option.id),
  };

  const onSubmit = async (data: DocumentFormInput) => {
    const { document_file, ...inputValues } = data;
    const formData = new FormData();
    if (document_file?.[0]) {
      formData.append('file', document_file[0]);
    }
    formData.append('input', new Blob([JSON.stringify(inputValues)], { type: 'application/json' }));
    const result = await dispatch(addDocument(formData));
    if (result.result) {
      onCreate?.(result.entities.documents[result.result]);
      setOpen(false);
    }
  };

  const form = (
    <DocumentForm
      initialValues={initialValues}
      onSubmit={onSubmit}
      handleClose={() => setOpen(false)}
      filters={filters}
    />
  );

  return (
    <>
      {/* Header placement (picker top-right): compact creation button. */}
      <ButtonCreate
        onClick={() => setOpen(true)}
        label={inline ? t('Create a new document') : undefined}
      />
      {inline
        ? (
            <Dialog
              open={open}
              handleClose={() => setOpen(false)}
              title={t('Create a new document')}
            >
              {form}
            </Dialog>
          )
        : (
            <Drawer
              open={open}
              handleClose={() => setOpen(false)}
              title={t('Create a new document')}
            >
              {form}
            </Drawer>
          )}
    </>
  );
};

export default CreateDocument;
