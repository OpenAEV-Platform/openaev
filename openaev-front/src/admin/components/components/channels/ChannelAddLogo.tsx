import { Button } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchDocuments } from '../../../../actions/Document';
import FileTransferDialog from '../../../../components/fields/FileTransferDialog';
import { useFormatter } from '../../../../components/i18n';
import { type RawDocument } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

interface Props { handleAddLogo: (documentId: string) => void }

const ChannelAddLogo: FunctionComponent<Props> = ({ handleAddLogo }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [open, setOpen] = useState(false);
  const [selectedDocument, setSelectedDocument] = useState<RawDocument | null>(null);

  useEffect(() => {
    if (open) {
      dispatch(fetchDocuments());
    }
    if (selectedDocument && selectedDocument.document_id) {
      handleAddLogo(selectedDocument.document_id);
    }
  }, [selectedDocument]);

  const handleOpen = () => {
    setOpen(true);
  };

  return (
    <div>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.DOCUMENTS}>
        <Button
          variant="outlined"
          color="secondary"
          onClick={handleOpen}
          style={{ marginTop: 20 }}
        >
          {t('Change logo')}
        </Button>
      </Can>
      {open && (
        <FileTransferDialog
          label={t('Select an image')}
          open={open}
          setOpen={setOpen}
          onAddDocument={setSelectedDocument}
          extensions={['png', 'jpg', 'jpeg', 'svg', 'gif']}
        >
        </FileTransferDialog>
      )}
    </div>
  );
};

export default ChannelAddLogo;
