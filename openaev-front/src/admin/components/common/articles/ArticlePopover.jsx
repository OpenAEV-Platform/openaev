import { Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import * as R from 'ramda';
import { Fragment, useContext, useState } from 'react';

import ButtonPopover from '../../../../components/common/ButtonPopover';
import CommonDialog from '../../../../components/common/dialog/Dialog';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { ArticleContext, PermissionsContext } from '../Context';
import ArticleForm from './ArticleForm';

// `inline`: rendered inside another drawer (inject form) - edit in a dialog
// instead of the standard edition drawer.
const ArticlePopover = ({ article, onRemoveArticle, disabled = false, inline = false }) => {
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
          <Button variant="outlined" color="primary" onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button variant="contained" color="error" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      {inline ? (
        <CommonDialog
          open={openEdit}
          handleClose={handleCloseEdit}
          title={t('Update the media pressure article')}
        >
          <ArticleForm
            editing
            onSubmit={onSubmitEdit}
            handleClose={handleCloseEdit}
            initialValues={initialValues}
            documentsIds={article.article_documents ?? []}
          />
        </CommonDialog>
      ) : (
        <Drawer
          open={openEdit}
          handleClose={handleCloseEdit}
          title={t('Update the media pressure article')}
        >
          <ArticleForm
            editing
            onSubmit={onSubmitEdit}
            handleClose={handleCloseEdit}
            initialValues={initialValues}
            documentsIds={article.article_documents ?? []}
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
            {t('Do you want to remove this media pressure article from the inject?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleCloseRemove}>{t('Cancel')}</Button>
          <Button variant="contained" color="primary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </Dialog>
    </Fragment>
  );
};

export default ArticlePopover;
