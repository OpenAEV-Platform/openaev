import { ControlPointOutlined } from '@mui/icons-material';
import { ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchDocuments } from '../../actions/Document';
import { type RawDocument } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { Can } from '../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';
import FileTransferDialog from './FileTransferDialog';

const useStyles = makeStyles()(theme => ({
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

interface Props {
  hasAttachments?: boolean;
  handleAddDocuments: (documents: {
    document_id: string;
    document_attached: boolean;
  }[]) => void;
  initialDocumentIds: string[];
  disabled?: boolean;
}

const MultipleFileLoader: FunctionComponent<Props> = ({
  hasAttachments = false,
  handleAddDocuments,
  initialDocumentIds,
  disabled = false,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  useDataLoader(() => {
    if (open) {
      dispatch(fetchDocuments());
    }
  }, [open]);

  const handleOpen = () => setOpen(true);

  const submitAddDocuments = (documents: RawDocument[]) => {
    handleAddDocuments(documents.filter(doc => doc.document_id !== undefined)
      .map((document: RawDocument) => ({
        document_id: document.document_id!,
        document_attached: hasAttachments,
      })));
  };

  return (
    <>
      <Can I={ACTIONS.ACCESS} a={SUBJECTS.DOCUMENTS}>
        <ListItemButton
          divider
          onClick={handleOpen}
          color="primary"
          disabled={disabled}
        >
          <ListItemIcon color="primary">
            <ControlPointOutlined color="primary" />
          </ListItemIcon>
          <ListItemText
            primary={t('Add documents')}
            classes={{ primary: classes.text }}
          />
        </ListItemButton>
      </Can>
      <FileTransferDialog
        label={t('Add documents')}
        open={open}
        setOpen={setOpen}
        multiple
        initialDocumentIds={initialDocumentIds}
        onSubmitAddDocuments={submitAddDocuments}
      >
      </FileTransferDialog>
    </>
  );
};

export default MultipleFileLoader;
