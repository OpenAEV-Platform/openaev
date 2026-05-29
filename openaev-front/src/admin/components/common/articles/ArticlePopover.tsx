import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { Fragment, useContext, useEffect, useState } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';

import ButtonPopover from '../../../../components/common/ButtonPopover';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import type { Article, ArticleUpdateInput } from '../../../../utils/api-types';
import { ArticleContext, PermissionsContext } from '../Context';
import ArticleForm from './ArticleForm';
import { resolveChannelId } from './ArticleUtils';

interface ArticlePopoverProps {
  article: Article;
  onRemoveArticle?: (articleId: string) => void;
  disabled?: boolean;
}

const ArticlePopover = ({ article, onRemoveArticle, disabled = false }: ArticlePopoverProps) => {
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
  const methods = useForm<ArticleUpdateInput>({
    defaultValues: {
      article_name: article.article_name,
      article_author: article.article_author,
      article_content: article.article_content,
      article_channel: resolveChannelId(article.article_channel),
      article_comments: article.article_comments,
      article_shares: article.article_shares,
      article_likes: article.article_likes,
      article_documents: article.article_documents,
    },
  });

  const { handleSubmit } = methods;

  // Edit action
  const handleOpenEdit = () => setOpenEdit(true);
  const handleCloseEdit = () => {
    methods.reset();
    setOpenEdit(false);
  };

  const onSubmitEdit: SubmitHandler<ArticleUpdateInput> = async (data) => {
    await onUpdateArticle(article, data);
    handleCloseEdit();
  };

  // Delete action
  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = async () => {
    await onDeleteArticle(article);
    handleCloseDelete();
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
    methods.reset({
      article_name: article.article_name,
      article_author: article.article_author,
      article_content: article.article_content,
      article_channel: resolveChannelId(article.article_channel),
      article_comments: article.article_comments,
      article_shares: article.article_shares,
      article_likes: article.article_likes,
      article_documents: article.article_documents,
    });
  }, [article, methods]);

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
          <Button onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={openEdit}
        onClose={handleCloseEdit}
        fullWidth={true}
        maxWidth="md"
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogTitle>{t('Update the media pressure article')}</DialogTitle>
        <DialogContent style={{ overflowX: 'hidden' }}>
          <FormProvider {...methods}>
            <form onSubmit={handleSubmit(onSubmitEdit)}>
              <ArticleForm
                editing
                handleClose={handleCloseEdit}
                documentsIds={article.article_documents}
              />
            </form>
          </FormProvider>
        </DialogContent>
      </Dialog>
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
