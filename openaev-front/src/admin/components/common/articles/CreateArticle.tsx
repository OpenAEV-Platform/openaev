import { zodResolver } from '@hookform/resolvers/zod';
import { Dialog, DialogContent, DialogTitle } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';

import ButtonCreate from '../../../../components/common/ButtonCreate';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { ArticleContext } from '../Context';
import ArticleForm from './ArticleForm';
import { type ArticleFormInput, articleFormSchema } from './ArticleUtils';

interface CreateArticleProps {
  openCreate: boolean;
  handleOpenCreate: () => void;
  handleCloseCreate: () => void;
  onCreate?: (articleId: string) => void;
}

const CreateArticle: FunctionComponent<CreateArticleProps> = ({
  onCreate,
  openCreate,
  handleOpenCreate,
  handleCloseCreate,
}) => {
  const { t } = useFormatter();

  // Context
  const { onAddArticle } = useContext(ArticleContext);

  const methods = useForm<ArticleFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(articleFormSchema(t)),
    defaultValues: {
      article_name: '',
      article_channel: '',
      article_content: '',
      article_author: '',
    },
  });

  const { handleSubmit, reset } = methods;

  // The form state lives outside the dialog, so it must be cleared on close
  // otherwise reopening shows the previously typed values.
  const handleClose = () => {
    reset();
    handleCloseCreate();
  };

  const onSubmit: SubmitHandler<ArticleFormInput> = async (data) => {
    const result = await onAddArticle(data);
    if (result.result) {
      onCreate?.(result.result);
      handleClose();
    }
  };

  return (
    <>
      {/* Same compact creation button whether standalone or in a picker header. */}
      <ButtonCreate onClick={handleOpenCreate} label={t('Create an article')} />
      <Dialog
        open={openCreate}
        slots={{ transition: Transition }}
        onClose={handleClose}
        fullWidth
        maxWidth="md"
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogTitle>{t('Create a new media pressure article')}</DialogTitle>
        <DialogContent style={{ overflowX: 'hidden' }}>
          <FormProvider {...methods}>
            <form onSubmit={handleSubmit(onSubmit)}>
              <ArticleForm
                editing={false}
                handleClose={handleClose}
              />
            </form>
          </FormProvider>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default CreateArticle;
