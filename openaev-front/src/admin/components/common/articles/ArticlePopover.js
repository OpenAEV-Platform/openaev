import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import * as R from 'ramda';
import { Fragment, useContext, useState } from 'react';

import ButtonPopover from '../../../../components/common/ButtonPopover.js';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { ArticleContext, PermissionsContext } from '../Context';
import ArticleForm from './ArticleForm';

const ArticlePopover = ({ article, onRemoveArticle, disabled = false }) => {
  // Standard hooks
  const { t } = useFormatter();

  // Context
  const { onUpdateArticle, onDeleteArticle } = useContext(ArticleContext);
  const { permissions } = useContext(PermissionsContext);

  // states
  const [openDelete, setOpenDelete] = useState(false);
  const [openRemove, setOpenRemove] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);

  // Edit action
  const handleOpenEdit = () => {
    setOpenEdit(true);
  };
  const handleCloseEdit = () => setOpenEdit(false);
  const onSubmitEdit = (data) => {
    const inputValues = {
      ...data,
      article_channel: data.article_channel.id,
    };
    return onUpdateArticle(article, inputValues).then(() => handleCloseEdit());
  };
    // Delete action
  const handleOpenDelete = () => {
    setOpenDelete(true);
  };
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    return onDeleteArticle(article).then(() => handleCloseDelete());
  };
  const handleOpenRemove = () => {
    setOpenRemove(true);
  };
  const handleCloseRemove = () => {
    setOpenRemove(false);
  };
  const submitRemove = () => {
    onRemoveArticle(article.article_id);
    handleCloseRemove();
  };
    // Rendering
  const initialValues = R.pipe(
    R.pick([
      'article_name',
      'article_content',
      'article_author',
      'article_shares',
      'article_likes',
      'article_comments',
      'article_channel',
    ]),
  )(article);

  // Button Popover
  const entries = [{
    label: 'Update',
    action: () => handleOpenEdit(),
    userRight: permissions.canManage,
  }, {
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: permissions.canManage,
  }];
  if (onRemoveArticle) entries.push({
    label: 'Remove from the inject',
    action: () => handleOpenRemove(),
    userRight: true,
  });

  return (
    <Fragment>
      <ButtonPopover
        entries={entries}
        variant="icon"
        disabled={!permissions.canManage || disabled}
      />
      <Dialog
        open={openDelete}
        TransitionComponent={Transition}
        onClose={handleCloseDelete}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this media pressure article?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        TransitionComponent={Transition}
        open={openEdit}
        onClose={handleCloseEdit}
        fullWidth={true}
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Update the media pressure article')}</DialogTitle>
        <DialogContent style={{ overflowX: 'hidden' }}>
          <ArticleForm
            editing
            onSubmit={onSubmitEdit}
            handleClose={handleCloseEdit}
            initialValues={initialValues}
            documentsIds={article.article_documents ?? []}
          />
        </DialogContent>
      </Dialog>
      <Dialog
        open={openRemove}
        TransitionComponent={Transition}
        onClose={handleCloseRemove}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to remove this media pressure article from the inject?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseRemove}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </Dialog>
    </Fragment>
  );
};

export default ArticlePopover;
