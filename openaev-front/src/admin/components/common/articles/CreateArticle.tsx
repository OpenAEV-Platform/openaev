import { Dialog, DialogContent, DialogTitle } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';

import ButtonCreate from '../../../../components/common/ButtonCreate';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type ArticleCreateInput } from '../../../../utils/api-types';
import { ArticleContext } from '../Context';
import ArticleForm from './ArticleForm';

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

  const methods = useForm<ArticleCreateInput>({
    defaultValues: {
      article_name: '',
      article_channel: '',
      article_content: '',
      article_author: '',
    },
  });

  const { handleSubmit, reset } = methods;

  const onSubmit: SubmitHandler<ArticleCreateInput> = async (data) => {
    const result = await onAddArticle(data);
    if (result.result) {
      onCreate?.(result.result);
      reset();
      handleCloseCreate();
    }
  };

  return (
    <>
      {/* Same compact creation button whether standalone or in a picker header. */}
      <ButtonCreate onClick={handleOpenCreate} label={t('Create an article')} />
      <Dialog
        open={openCreate}
        slots={{ transition: Transition }}
        onClose={handleCloseCreate}
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
                handleClose={handleCloseCreate}
              />
            </form>
          </FormProvider>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default CreateArticle;
