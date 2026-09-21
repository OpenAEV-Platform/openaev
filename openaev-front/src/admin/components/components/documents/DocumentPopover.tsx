import { FiberManualRecord } from '@mui/icons-material';
import {
  Button,
  Dialog as MuiDialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  List,
  ListItem,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';

import { deleteDocument, updateDocument } from '../../../../actions/Document';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import Dialog from '../../../../components/common/dialog/Dialog';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { craftedDocumentFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import Transition from '../../../../components/common/Transition';
import ContextLink from '../../../../components/ContextLink';
import { useFormatter } from '../../../../components/i18n';
import {
  ATOMIC_BASE_URL,
  CHALLENGE_BASE_URL,
  CHANNEL_BASE_URL,
  PAYLOAD_BASE_URL,
  SCENARIO_BASE_URL,
  SECURITY_PLATFORM_BASE_URL,
  SIMULATION_BASE_URL,
} from '../../../../constants/BaseUrls';
import { type Document, type DocumentRelationsOutput, type RelatedEntityOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import DocumentForm, { type DocumentFormInput } from './DocumentForm';

type RelationType = keyof DocumentRelationsOutput;

const entityPaths: Record<RelationType, (item: RelatedEntityOutput) => string> = {
  atomicTestings: item => `${ATOMIC_BASE_URL}/${item.id}`,
  simulations: item => `${SIMULATION_BASE_URL}/${item.id}`,
  channels: item => `${CHANNEL_BASE_URL}/${item.id}`,
  scenarioArticles: item => `${SCENARIO_BASE_URL}/${item.context}/definition`,
  simulationArticles: item => `${SIMULATION_BASE_URL}/${item.context}/definition`,
  payloads: item => `${PAYLOAD_BASE_URL}?query=${craftedDocumentFilter(item, 'payload_name', 'payloads')}`,
  scenarioInjects: item => `${SCENARIO_BASE_URL}/${item.context}/injects?query=${craftedDocumentFilter(item, 'inject_title', `${item.context}-injects`)}`,
  simulationInjects: item => `${SIMULATION_BASE_URL}/${item.context}/injects?query=${craftedDocumentFilter(item, 'inject_title', `${item.context}-injects`)}`,
  challenges: item => `${CHALLENGE_BASE_URL}?search=${item.name}`,
  securityPlatforms: item => `${SECURITY_PLATFORM_BASE_URL}?search=${item.name}`,
};

// Ordered entity types
const renderOrder: RelationType[] = ['atomicTestings', 'scenarioInjects', 'simulationInjects', 'simulations', 'payloads', 'channels', 'scenarioArticles', 'simulationArticles', 'challenges', 'securityPlatforms'];

// Structural subset shared by Document, RawDocument and RawPaginationDocument.
export type PopoverDocument = Partial<Document> & { document_id?: string };

interface Props {
  document: PopoverDocument;
  disabled?: boolean;
  attached?: boolean;
  inline?: boolean;
  onRemoveDocument?: (documentId: string) => void;
  onToggleAttach?: (documentId: string) => void;
  onUpdate?: (document: Document) => void;
  onDelete?: (documentId: string) => void;
  managedMessage?: string;
}

const DocumentPopover: FunctionComponent<Props> = ({
  document,
  disabled = false,
  attached = false,
  inline = false,
  onRemoveDocument,
  onToggleAttach,
  onUpdate,
  onDelete,
  managedMessage,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const documentId = document.document_id ?? '';

  const [openDelete, setOpenDelete] = useState(false);
  const [relations, setRelations] = useState<DocumentRelationsOutput | null>(null);
  const [loadingRelations, setLoadingRelations] = useState(false);
  const [isUsedInPayloads, setIsUsedInPayloads] = useState(false);
  const [isUsedAsPlatformLogo, setIsUsedAsPlatformLogo] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [openRemove, setOpenRemove] = useState(false);

  const handleCloseEdit = () => setOpenEdit(false);

  const onSubmitEdit = async (data: DocumentFormInput) => {
    // The file itself is not editable: only the document metadata is updated.
    const { document_file: _file, ...inputValues } = data;
    const result = await dispatch(updateDocument(documentId, inputValues));
    onUpdate?.(result.entities.documents[result.result]);
    handleCloseEdit();
  };

  const handleOpenDelete = () => {
    setOpenDelete(true);
    setLoadingRelations(true);
    fetch(buildTenantApiPath(`/api/documents/${documentId}/relations`))
      .then(res => res.json())
      .then((data: DocumentRelationsOutput) => setRelations(data))
      .catch(() => setRelations(null))
      .finally(() => setLoadingRelations(false));
  };

  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    dispatch(deleteDocument(documentId)).then(() => {
      onDelete?.(documentId);
    });
    handleCloseDelete();
  };

  useEffect(() => {
    if (relations) {
      setIsUsedInPayloads(!!relations.payloads?.length);
      // Security platform logos are uploaded by collectors and referenced by the
      // platform: deleting them would break the platform icon everywhere.
      setIsUsedAsPlatformLogo(!!relations.securityPlatforms?.length);
    }
  }, [relations]);

  const renderRelations = (entities: DocumentRelationsOutput) => renderOrder.map((type) => {
    const items = entities[type];
    if (!items?.length) return null;
    return (
      <div key={type}>
        <Typography gutterBottom>{t(type)}</Typography>
        <List dense>
          {items.map(item => (
            <ListItem key={item.id}>
              <FiberManualRecord sx={{
                fontSize: 8,
                marginRight: 1,
              }}
              />
              <ContextLink title={item.name ?? ''} url={entityPaths[type](item)} />
            </ListItem>
          ))}
        </List>
      </div>
    );
  });

  const renderDialogText = () => {
    if (loadingRelations) {
      return <div>{t('Loading relations...')}</div>;
    }
    if (!relations) {
      return t('Unable to load relations.');
    }

    const hasRelations = Object.values(relations).some(list => list.length > 0);

    return (
      <>
        {hasRelations && (
          <>
            <Typography gutterBottom>
              {t('The document is used in the following sections:')}
            </Typography>
            {renderRelations(relations)}
          </>
        )}
        <Typography sx={{ paddingTop: theme.spacing(2) }}>
          {(() => {
            if (isUsedInPayloads) {
              return t('A document used in a payload can\'t be deleted.');
            }
            if (isUsedAsPlatformLogo) {
              return t('A document used as a security platform logo can\'t be deleted.');
            }
            return t('Do you want to delete this document?');
          })()}
        </Typography>
      </>
    );
  };

  const handleCloseRemove = () => setOpenRemove(false);

  const submitRemove = () => {
    onRemoveDocument?.(documentId);
    handleCloseRemove();
  };

  const initialValues: Partial<DocumentFormInput> = {
    document_description: document.document_description ?? '',
    document_tags: document.document_tags ?? [],
    document_exercises: document.document_exercises ?? [],
    document_scenarios: document.document_scenarios ?? [],
  };

  const entries: PopoverEntry[] = [];
  if (onUpdate) entries.push({
    label: t('Update'),
    action: () => setOpenEdit(true),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.DOCUMENTS),
    disabled: !!managedMessage,
    disabledMessage: managedMessage,
  });
  if (onToggleAttach) entries.push({
    label: attached ? t('Disable attachment') : t('Enable attachment'),
    action: () => onToggleAttach(documentId),
    userRight: true,
  });
  if (onRemoveDocument) entries.push({
    label: t('Remove from the element'),
    action: () => setOpenRemove(true),
    userRight: true,
  });
  if (!onRemoveDocument) entries.push({
    label: t('Delete'),
    action: () => handleOpenDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.DOCUMENTS),
    disabled: !!managedMessage,
    disabledMessage: managedMessage,
  });

  const editForm = (
    <DocumentForm
      initialValues={initialValues}
      editing
      onSubmit={onSubmitEdit}
      handleClose={handleCloseEdit}
    />
  );

  return (
    <div>
      <ButtonPopover entries={entries} disabled={disabled} variant="icon" />

      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={!isUsedInPayloads && !isUsedAsPlatformLogo ? submitDelete : null}
        text={t('Do you want to delete this document?')}
        richContent={renderDialogText()}
      />

      {inline
        ? (
            <Dialog
              open={openEdit}
              handleClose={handleCloseEdit}
              title={t('Update the document')}
            >
              {editForm}
            </Dialog>
          )
        : (
            <Drawer
              open={openEdit}
              handleClose={handleCloseEdit}
              title={t('Update the document')}
            >
              {editForm}
            </Drawer>
          )}

      <MuiDialog
        open={openRemove}
        onClose={handleCloseRemove}
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)
              ? t('Do you want to remove the document from the element?')
              : t('You are about to remove an element that you will not be able to bring back due to access restriction. Are you sure you want to continue?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleCloseRemove}>
            {t('Cancel')}
          </Button>
          <Button variant="contained" color="primary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </MuiDialog>
    </div>
  );
};

export default DocumentPopover;
