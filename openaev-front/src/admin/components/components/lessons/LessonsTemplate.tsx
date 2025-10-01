import { HelpOutlined } from '@mui/icons-material';
import { GridLegacy, List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText, Paper, Typography } from '@mui/material';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type UserHelper } from '../../../../actions/helper';
import { fetchLessonsTemplateCategories, fetchLessonsTemplateQuestions } from '../../../../actions/Lessons';
import { type LessonsTemplatesHelper } from '../../../../actions/lessons/lesson-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type LessonsTemplateCategory, type LessonsTemplateQuestion } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CreateLessonsTemplateCategory from './categories/CreateLessonsTemplateCategory';
import LessonsTemplateCategoryPopover from './categories/LessonsTemplateCategoryPopover';
import CreateLessonsTemplateQuestion from './categories/questions/CreateLessonsTemplateQuestion';
import LessonsTemplateQuestionPopover from './categories/questions/LessonsTemplateQuestionPopover';

const useStyles = makeStyles()(() => ({
  container: {
    display: 'flex',
    alignItems: 'center',
  },
}));

const LessonsTemplate = () => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { lessonsTemplateId } = useParams() as { lessonsTemplateId: string };

  // Datas
  const {
    categories,
    questions,
  }: {
    categories: LessonsTemplateCategory[];
    questions: LessonsTemplateQuestion[];
  } = useHelper((helper: LessonsTemplatesHelper & UserHelper) => {
    return {
      categories: helper.getLessonsTemplateCategories(lessonsTemplateId),
      questions: helper.getLessonsTemplateQuestions(),
    };
  });
  useDataLoader(() => {
    dispatch(fetchLessonsTemplateCategories(lessonsTemplateId));
    dispatch(fetchLessonsTemplateQuestions(lessonsTemplateId));
  });

  // Utils
  const categoriesSorted = categories
    .sort((c1, c2) => ((c1.lessons_template_category_order ?? 0) > (c2.lessons_template_category_order ?? 0) ? 1 : -1));
  const sortQuestions = (qs: LessonsTemplateQuestion[]) => {
    return qs
      .sort((q1, q2) => ((q1.lessons_template_question_order ?? 0) > (q2.lessons_template_question_order ?? 0) ? 1 : -1));
  };

  return (
    <>
      <GridLegacy container columnSpacing={3} rowSpacing={5} style={{ marginTop: '0px' }}>
        {categoriesSorted.map((category) => {
          const questionsSorted = sortQuestions(
            questions.filter(q => q.lessons_template_question_category === category.lessonstemplatecategory_id),
          );
          return (
            <GridLegacy key={category.lessonstemplatecategory_id} item xs={6}>
              <div className={classes.container}>
                <Typography variant="h2" margin="0">
                  {category.lessons_template_category_name}
                </Typography>
                <LessonsTemplateCategoryPopover
                  lessonsTemplateId={lessonsTemplateId}
                  lessonsTemplateCategory={category}
                />
              </div>
              <Typography variant="h3">
                {category.lessons_template_category_description}
              </Typography>
              <Paper variant="outlined">
                <List disablePadding>
                  {questionsSorted.map((question) => {
                    return (
                      <ListItem
                        key={question.lessonstemplatequestion_id}
                        divider
                      >
                        <ListItemIcon>
                          <HelpOutlined />
                        </ListItemIcon>
                        <ListItemText
                          primary={question.lessons_template_question_content}
                          secondary={question.lessons_template_question_explanation ?? t('No explanation')}
                        />
                        <ListItemSecondaryAction>
                          <LessonsTemplateQuestionPopover
                            lessonsTemplateId={lessonsTemplateId}
                            lessonsTemplateCategoryId={category.lessonstemplatecategory_id}
                            lessonsTemplateQuestion={question}
                          />
                        </ListItemSecondaryAction>
                      </ListItem>
                    );
                  })}
                  <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
                    <CreateLessonsTemplateQuestion
                      lessonsTemplateId={lessonsTemplateId}
                      lessonsTemplateCategoryId={category.lessonstemplatecategory_id}
                    />
                  </Can>

                </List>
              </Paper>
            </GridLegacy>
          );
        })}
      </GridLegacy>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
        <CreateLessonsTemplateCategory lessonsTemplateId={lessonsTemplateId} />
      </Can>
    </>
  );
};

export default LessonsTemplate;
