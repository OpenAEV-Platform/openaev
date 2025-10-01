import { FiberManualRecord } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, List, ListItem, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useContext, useEffect, useState } from 'react';

import { deleteDocument, updateDocument } from '../../../../actions/Document';
import { fetchExercises } from '../../../../actions/Exercise';
import { fetchScenarios } from '../../../../actions/scenarios/scenario-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover.js';
import DialogDelete from '../../../../components/common/DialogDelete.js';
import Drawer from '../../../../components/common/Drawer';
import { craftedDocumentFilter } from '../../../../components/common/queryable/filter/FilterUtils.js';
import Transition from '../../../../components/common/Transition';
import ContextLink from '../../../../components/ContextLink.js';
import { useFormatter } from '../../../../components/i18n';
import { ATOMIC_BASE_URL, CHALLENGE_BASE_URL, CHANNEL_BASE_URL, PAYLOAD_BASE_URL, SCENARIO_BASE_URL, SECURITY_PLATFORM_BASE_URL, SIMULATION_BASE_URL } from '../../../../constants/BaseUrls.js';
import { useHelper } from '../../../../store';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { exerciseOptions, scenarioOptions, tagOptions } from '../../../../utils/Option';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types.js';
import DocumentForm from './DocumentForm';

const entityPaths = {
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
const renderOrder = ['atomicTestings', 'scenarioInjects', 'simulationInjects', 'simulations', 'payloads', 'channels', 'scenarioArticles', 'simulationArticles', 'challenges', 'securityPlatforms'];

const DocumentPopover = (props) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const { document, disabled, onRemoveDocument, attached, onToggleAttach, inline, onUpdate, onDelete } = props;

  // Fetching data
  const { tagsMap, exercisesMap, scenariosMap } = useHelper(helper => ({
    tagsMap: helper.getTagsMap(),
    exercisesMap: helper.getExercisesMap(),
    scenariosMap: helper.getScenariosMap(),
  }));
  if (!props.scenariosAndExercisesFetched) {
    useDataLoader(() => {
      dispatch(fetchExercises());
      dispatch(fetchScenarios());
    });
  }

  const [openDelete, setOpenDelete] = useState(false);
  const [relations, setRelations] = useState(null);
  const [loadingRelations, setLoadingRelations] = useState(false);
  const [isUsedInPayloads, setIsUsedInPayloads] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [openRemove, setOpenRemove] = useState(false);

  const handleOpenEdit = () => {
    setOpenEdit(true);
  };

  const handleCloseEdit = () => {
    setOpenEdit(false);
  };

  const onSubmitEdit = (data) => {
    const inputValues = R.pipe(R.assoc('document_tags', R.pluck('id', data.document_tags)), R.assoc('document_exercises', R.pluck('id', data.document_exercises)), R.assoc('document_scenarios', R.pluck('id', data.document_scenarios)))(data);
    return dispatch(updateDocument(document.document_id, inputValues))
      .then((result) => {
        if (onUpdate) {
          const updated = result.entities.documents[result.result];
          onUpdate(updated);
        }
        handleCloseEdit();
      });
  };

  const handleOpenDelete = () => {
    setOpenDelete(true);
    setLoadingRelations(true);
    fetch(`/api/documents/${document.document_id}/relations`)
      .then(res => res.json())
      .then((data) => {
        setRelations(data);
      })
      .catch(() => {
        setRelations(null);
      })
      .finally(() => {
        setLoadingRelations(false);
      });
  };

  const handleCloseDelete = () => {
    setOpenDelete(false);
  };

  const submitDelete = () => {
    dispatch(deleteDocument(document.document_id)).then(() => {
      if (onDelete) {
        onDelete(document.document_id);
      }
    });
    handleCloseDelete();
  };

  useEffect(() => {
    if (relations) {
      setIsUsedInPayloads(!!relations.payloads?.length);
    }
  }, [relations]);

  const buildEntityPath = (type, item) => {
    const pathFn = entityPaths[type];
    if (!pathFn) return '#';
    return pathFn.length > 0 ? pathFn(item) : pathFn();
  };

  const renderRelations = (entities) => {
    return renderOrder.map((type) => {
      const items = entities[type];
      if (!items?.length) return null;

      return (
        <div key={type}>
          <Typography gutterBottom>
            {t(type)}
          </Typography>
          <List dense>
            {items.map(item => (
              <ListItem key={item.id}>
                <FiberManualRecord sx={{
                  fontSize: 8,
                  marginRight: 1,
                }}
                />
                <ContextLink
                  title={item.name}
                  url={buildEntityPath(type, item)}
                />
              </ListItem>
            ))}
          </List>
        </div>
      );
    });
  };

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
          {isUsedInPayloads ? t('A document used in a payload can\'t be deleted.') : t('Do you want to delete this document?')}
        </Typography>
      </>
    );
  };

  const handleOpenRemove = () => {
    setOpenRemove(true);
  };

  const handleCloseRemove = () => {
    setOpenRemove(false);
  };

  const submitRemove = () => {
    onRemoveDocument(document.document_id);
    handleCloseRemove();
  };

  const handleToggleAttachement = () => {
    onToggleAttach(document.document_id);
  };

  const documentTags = tagOptions(document.document_tags, tagsMap);
  const documentExercises = exerciseOptions(document.document_exercises, exercisesMap);
  const documentScenarios = scenarioOptions(document.document_scenarios, scenariosMap);
  const initialValues = R.pipe(R.assoc('document_tags', documentTags), R.assoc('document_exercises', documentExercises), R.assoc('document_scenarios', documentScenarios), R.pick(['document_name', 'document_description', 'document_type', 'document_tags', 'document_exercises', 'document_scenarios']))(document);

  const entries = [];
  if (onUpdate) entries.push({
    label: t('Update'),
    action: () => handleOpenEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.DOCUMENTS),
  });
  if (onToggleAttach) entries.push({
    label: attached ? t('Disable attachment') : t('Enable attachment'),
    action: () => handleToggleAttachement(),
    userRight: true,
  });
  if (onRemoveDocument) entries.push({
    label: t('Remove from the element'),
    action: () => handleOpenRemove(),
    userRight: true,
  });
  if (!onRemoveDocument) entries.push({
    label: t('Delete'),
    action: () => handleOpenDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.DOCUMENTS),
  });

  return (
    <div>
      <ButtonPopover entries={entries} disabled={disabled} variant="icon" />

      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={!isUsedInPayloads ? submitDelete : null}
        richContent={renderDialogText()}
      />

      {inline ? (
        <Dialog
          open={openEdit}
          handleClose={handleCloseEdit}
          title={t('Update the document')}
        >
          <DocumentForm
            initialValues={initialValues}
            editing
            onSubmit={onSubmitEdit}
            handleClose={handleCloseEdit}
          />
        </Dialog>
      ) : (
        <Drawer
          open={openEdit}
          handleClose={handleCloseEdit}
          title={t('Update the document')}
        >
          <DocumentForm
            initialValues={initialValues}
            editing
            onSubmit={onSubmitEdit}
            handleClose={handleCloseEdit}
          />
        </Drawer>
      )}

      <Dialog
        open={openRemove}
        TransitionComponent={Transition}
        onClose={handleCloseRemove}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)
              ? t('Do you want to remove the document from the element?')
              : t('You are about to remove an element that you will not be able to bring back due to access restriction. Are you sure you want to continue ?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseRemove}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
};

export default DocumentPopover;
