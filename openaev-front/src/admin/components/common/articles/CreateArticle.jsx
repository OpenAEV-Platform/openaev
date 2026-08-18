import { Dialog, DialogContent, DialogTitle } from '@mui/material';
import { useContext } from 'react';

import ButtonCreate from '../../../../components/common/ButtonCreate';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { ArticleContext } from '../Context';
import ArticleForm from './ArticleForm';

const CreateArticle = (props) => {
  const { onCreate, openCreate, handleOpenCreate, handleCloseCreate } = props;
  const { t } = useFormatter();

  // Context
  const { onAddArticle } = useContext(ArticleContext);

  const onSubmit = (data) => {
    const inputValues = {
      ...data,
      article_channel: data.article_channel.id,
    };
    return onAddArticle(inputValues).then(
      (result) => {
        if (result.result) {
          if (onCreate) {
            onCreate(result.result);
          }
          return handleCloseCreate();
        }
        return result;
      },
    );
  };
  return (
    <>
      {/* Same compact creation button whether standalone or in a picker header. */}
      <ButtonCreate size="sm" onClick={handleOpenCreate} label={t('Create an article')} />
      <Dialog
        open={openCreate}
        TransitionComponent={Transition}
        onClose={handleCloseCreate}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Create a new media pressure article')}</DialogTitle>
        <DialogContent style={{ overflowX: 'hidden' }}>
          <ArticleForm
            editing={false}
            onSubmit={onSubmit}
            handleClose={handleCloseCreate}
            initialValues={{
              article_name: '',
              article_channel: '',
            }}
          />
        </DialogContent>
      </Dialog>
    </>
  );
};

export default CreateArticle;
