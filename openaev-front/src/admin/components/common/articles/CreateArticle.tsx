import { Add, ControlPointOutlined } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle, IconButton, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useContext } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type ArticleCreateInput } from '../../../../utils/api-types';
import { ArticleContext } from '../Context';
import ArticleForm from './ArticleForm';

const useStyles = makeStyles()(theme => ({
  createButton: {
    float: 'left',
    marginTop: -15,
  },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

interface CreateArticleProps {
  onCreate?: (articleId: string) => void;
  inline?: boolean;
  openCreate: boolean;
  isOpen: (open: boolean) => void;
}

const CreateArticle = ({
  onCreate,
  inline,
  openCreate,
  isOpen,
}: CreateArticleProps) => {
  const { classes } = useStyles();
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
      if (onCreate) {
        onCreate(result.result);
      }
      reset();
      isOpen(false);
    }
  };

  return (
    <>
      {inline ? (
        <ListItemButton divider onClick={() => isOpen(true)} color="primary">
          <ListItemIcon color="primary">
            <ControlPointOutlined color="primary" />
          </ListItemIcon>
          <ListItemText
            primary={t('Create a new media pressure article')}
            classes={{ primary: classes.text }}
          />
        </ListItemButton>
      ) : (
        <IconButton
          color="primary"
          aria-label="Add"
          onClick={() => isOpen(true)}
          classes={{ root: classes.createButton }}
          size="large"
        >
          <Add fontSize="small" />
        </IconButton>
      )}
      <Dialog
        open={openCreate}
        TransitionComponent={Transition}
        onClose={() => isOpen(false)}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Create a new media pressure article')}</DialogTitle>
        <DialogContent style={{ overflowX: 'hidden' }}>
          <FormProvider {...methods}>
            <form onSubmit={handleSubmit(onSubmit)}>
              <ArticleForm
                editing={false}
                handleClose={() => isOpen(false)}
              />
            </form>
          </FormProvider>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default CreateArticle;
