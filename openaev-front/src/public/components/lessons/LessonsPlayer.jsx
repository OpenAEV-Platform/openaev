import { Button, Dialog, DialogActions, DialogContent, DialogContentText, GridLegacy, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useContext, useState } from 'react';
import { Form } from 'react-final-form';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { ViewLessonContext } from '../../../admin/components/common/Context';
import Transition from '../../../components/common/Transition';
import Empty from '../../../components/Empty';
import OldTextField from '../../../components/fields/OldTextField';
import SliderField from '../../../components/fields/SliderField';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';

const useStyles = makeStyles()(() => ({
  root: {
    position: 'relative',
    flexGrow: 1,
    padding: 60,
  },
  logo: {
    width: 100,
    margin: '0px 0px 10px 0px',
  },
  container: {
    margin: '0 auto',
    width: '90%',
  },
  paper: {
    position: 'relative',
    padding: '10px 15px 20px 15px',
    overflow: 'hidden',
  },
}));

const LessonsPlayer = (props) => {
  const { source, lessonsCategories, lessonsQuestions, lessonsAnswers, permissions } = props;

  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();
  const [openValidate, setOpenValidate] = useState(false);

  // Context
  const { onAddLessonsAnswers, onFetchPlayerLessonsAnswers } = useContext(ViewLessonContext);

  const submitForm = (data) => {
    const idsArray = Array.from(new Set(Object.keys(data).map(key => key.split('_')[0])));
    return Promise.all(
      lessonsQuestions
        .filter(question => idsArray.includes(question.lessonsquestion_id)) // Filter questions
        .map((question) => {
          const answerData = {
            lessons_answer_score: data[`${question.lessonsquestion_id}_score`],
            lessons_answer_positive: data[`${question.lessonsquestion_id}_positive`],
            lessons_answer_negative: data[`${question.lessonsquestion_id}_negative`],
          };
          return onAddLessonsAnswers(question.lessons_question_category, question.lessonsquestion_id, answerData);
        }),
    ).then(() => {
      setOpenValidate(false);
      onFetchPlayerLessonsAnswers();
    });
  };
  const sortCategories = R.sortWith([R.ascend(R.prop('lessons_category_order'))]);
  const sortQuestions = R.sortWith([R.ascend(R.prop('lessons_question_order'))]);
  const sortedCategories = sortCategories(R.filter(n => n.lessons_category_users.includes(source.finalUserId), lessonsCategories));

  const initialValues = R.pipe(R.map(n => ({
    [`${n.lessons_answer_question}_score`]: n.lessons_answer_score || 0,
    [`${n.lessons_answer_question}_positive`]: n.lessons_answer_positive || 0,
    [`${n.lessons_answer_question}_negative`]: n.lessons_answer_negative || 0,
  })), R.mergeAll)(lessonsAnswers);
  if (source) {
    return (
      <div className={classes.root}>
        {permissions.isLoggedIn && permissions.canAccess && (
          <Button
            color="secondary"
            variant="outlined"
            component={Link}
            to={`/lessons/${source.type}/${source.id}?user=${source.finalUserId}&preview=true`}
            style={{
              position: 'absolute',
              top: 20,
              right: 20,
            }}
          >
            {t('Switch to preview mode')}
          </Button>
        )}
        {permissions.isLoggedIn && permissions.canAccess && (
          <Button
            color="primary"
            variant="outlined"
            component={Link}
            to={`/admin/${source.type}s/${source.id}/lessons`}
            style={{
              position: 'absolute',
              top: 20,
              left: 20,
            }}
          >
            {t('Back to administration')}
          </Button>
        )}
        <div className={classes.container}>
          <div style={{
            margin: '0 auto',
            textAlign: 'center',
          }}
          >
            <img src={theme.logo} alt="logo" className={classes.logo} />
          </div>
          <Typography
            variant="h1"
            style={{
              textAlign: 'center',
              fontSize: 40,
            }}
          >
            {source.name}
          </Typography>
          <Typography
            variant="h2"
            style={{ textAlign: 'center' }}
          >
            {source.subtitle}
          </Typography>
          {lessonsCategories.length === 0 && (
            <div style={{ marginTop: 150 }}>
              <Empty
                message={t(`No lessons learned categories in this ${source.type} yet.`)}
              />
            </div>
          )}
        </div>
        <Form
          keepDirtyOnReinitialize
          initialValues={initialValues}
          onSubmit={submitForm}
        >
          {({ handleSubmit, submitting }) => {
            return (
              <form id="lessonsAnswersForm" onSubmit={handleSubmit}>
                {sortedCategories.map((category) => {
                  const questions = sortQuestions(lessonsQuestions.filter(n => n.lessons_question_category === category.lessonscategory_id));
                  return (
                    <div
                      key={category.lessonscategory_id}
                      style={{ marginTop: 70 }}
                    >
                      <Typography variant="h2">
                        {category.lessons_category_name}
                      </Typography>
                      {questions.map((question) => {
                        return (
                          <Paper
                            key={question.lessonsquestion_id}
                            variant="outlined"
                            classes={{ root: classes.paper }}
                            style={{ marginTop: 14 }}
                          >
                            <GridLegacy
                              container
                              spacing={3}
                              style={{ marginTop: -10 }}
                            >
                              <GridLegacy item xs={3}>
                                <Typography
                                  variant="h4"
                                  style={{ marginBottom: 15 }}
                                >
                                  {t('Question')}
                                </Typography>
                                <Typography variant="body1">
                                  <strong>
                                    {question.lessons_question_content}
                                  </strong>
                                </Typography>
                                <Typography variant="body2">
                                  {question.lessons_question_explanation || t('No explanation')}
                                </Typography>
                              </GridLegacy>
                              <GridLegacy item xs={3}>
                                <Typography
                                  variant="h4"
                                  style={{ marginBottom: 15 }}
                                >
                                  {t('Global evaluation')}
                                </Typography>
                                <Typography variant="body1">
                                  {t('Your overall evaluation about this question.')}
                                </Typography>
                                <SliderField
                                  disabled={lessonsAnswers.length > 0}
                                  name={`${question.lessonsquestion_id}_score`}
                                  step={10}
                                  min={0}
                                  max={100}
                                  defaultValue={0}
                                />
                              </GridLegacy>
                              <GridLegacy item xs={3}>
                                <Typography variant="h4">
                                  {t('What worked well')}
                                </Typography>
                                <OldTextField
                                  disabled={lessonsAnswers.length > 0}
                                  style={{ marginTop: 10 }}
                                  name={`${question.lessonsquestion_id}_positive`}
                                  label={t('Comment (optional)')}
                                  multiline
                                  rows={2}
                                  fullWidth
                                />
                              </GridLegacy>
                              <GridLegacy item xs={3}>
                                <Typography variant="h4">
                                  {t('What didn\'t work well')}
                                </Typography>
                                <OldTextField
                                  disabled={lessonsAnswers.length > 0}
                                  style={{ marginTop: 10 }}
                                  name={`${question.lessonsquestion_id}_negative`}
                                  label={t('Comment (optional)')}
                                  multiline
                                  fullWidth
                                  rows={2}
                                />
                              </GridLegacy>
                            </GridLegacy>
                          </Paper>
                        );
                      })}
                    </div>
                  );
                })}
                {sortedCategories.length > 0 && (
                  <div style={{
                    margin: '50px auto',
                    textAlign: 'center',
                  }}
                  >
                    <Button
                      color="secondary"
                      variant="contained"
                      onClick={() => setOpenValidate(true)}
                      disabled={lessonsAnswers.length > 0 || submitting || source.isUserAbsent}
                      size="large"
                    >
                      {t('Submit')}
                    </Button>
                  </div>
                )}
                <Dialog
                  open={openValidate}
                  TransitionComponent={Transition}
                  onClose={() => setOpenValidate(false)}
                  PaperProps={{ elevation: 1 }}
                >
                  <DialogContent>
                    <DialogContentText>
                      {t('Do you want to submit your answers? You will not be able to change them later.')}
                    </DialogContentText>
                  </DialogContent>
                  <DialogActions>
                    <Button
                      onClick={() => setOpenValidate(false)}
                      disabled={submitting}
                    >
                      {t('Cancel')}
                    </Button>
                    <Button
                      color="secondary"
                      onClick={handleSubmit}
                      disabled={submitting}
                    >
                      {t('Submit')}
                    </Button>
                  </DialogActions>
                </Dialog>
              </form>
            );
          }}
        </Form>
      </div>
    );
  }
  return <Loader />;
};

export default LessonsPlayer;
