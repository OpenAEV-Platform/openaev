import { Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { Fragment, useContext, useEffect, useState } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';

import ButtonPopover from '../../../../components/common/ButtonPopover';
import CommonDialog from '../../../../components/common/dialog/Dialog';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import type { Article, ArticleUpdateInput } from '../../../../utils/api-types';
import { ArticleContext, PermissionsContext } from '../Context';
import ArticleForm from './ArticleForm';

interface ArticlePopoverProps {
  article: Article;
  onRemoveArticle?: (articleId: string) => void;
  disabled?: boolean;
  /** Rendered inside another drawer (inject form): edit in a dialog instead of
      the standard edition drawer, to avoid a drawer over a drawer. */
  inline?: boolean;
}

const buildFormValues = (article: Article): ArticleUpdateInput => ({
  article_name: article.article_name ?? '',
  article_author: article.article_author,
  article_content: article.article_content,
  article_channel: article.article_channel,
  article_comments: article.article_comments,
  article_shares: article.article_shares,
  article_likes: article.article_likes,
});

const ArticlePopover = ({ article, onRemoveArticle, disabled = false, inline = false }: ArticlePopoverProps) => {
  // Standard hooks
  const { t } = useFormatter();

  // Context
  const { onUpdateArticle, onDeleteArticle } = useContext(ArticleContext);
  const { permissions } = useContext(PermissionsContext);

  // states
  const [openDelete, setOpenDelete] = useState(false);
  const [openRemove, setOpenRemove] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);

  // Form Initialisation
  const methods = useForm<ArticleUpdateInput>({ defaultValues: buildFormValues(article) });

  const { handleSubmit } = methods;

  // Edit action
  const handleOpenEdit = () => setOpenEdit(true);
  const handleCloseEdit = () => {
    methods.reset();
    setOpenEdit(false);
  };

  const onSubmitEdit: SubmitHandler<ArticleUpdateInput> = (data) => {
    const result = onUpdateArticle(article, data);
    if (result) {
      handleCloseEdit();
    }
  };

  // Delete action
  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    const result = onDeleteArticle(article);
    if (result) {
      handleCloseDelete();
    }
  };

  // Remove action
  const handleOpenRemove = () => setOpenRemove(true);
  const handleCloseRemove = () => setOpenRemove(false);

  const submitRemove = () => {
    if (onRemoveArticle) {
      onRemoveArticle(article.article_id);
    }
    handleCloseRemove();
  };

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

  useEffect(() => {
    methods.reset(buildFormValues(article));
  }, [article, methods]);

  const editForm = (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(onSubmitEdit)}>
        <ArticleForm
          editing
          handleClose={handleCloseEdit}
          documentsIds={article.article_documents ?? []}
        />
      </form>
    </FormProvider>
  );

  return (
    <Fragment>
      <ButtonPopover
        entries={entries}
        variant="icon"
        disabled={!permissions.canManage || disabled}
      />
      <Dialog
        open={openDelete}
        onClose={handleCloseDelete}
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
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
      {inline
        ? (
            <CommonDialog
              open={openEdit}
              handleClose={handleCloseEdit}
              title={t('Update the media pressure article')}
            >
              {editForm}
            </CommonDialog>
          )
        : (
            <Drawer
              open={openEdit}
              handleClose={handleCloseEdit}
              title={t('Update the media pressure article')}
            >
              {editForm}
            </Drawer>
          )}
      <Dialog
        open={openRemove}
        onClose={handleCloseRemove}
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
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
