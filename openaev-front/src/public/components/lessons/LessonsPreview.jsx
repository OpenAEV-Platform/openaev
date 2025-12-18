import { Button, GridLegacy, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { Form } from 'react-final-form';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

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

const LessonsPreview = (props) => {
  const {
    source,
    lessonsCategories,
    lessonsQuestions,
    permissions,
  } = props;

  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();

  const validate = (values) => {
    const errors = {};
    const requiredFields = R.flatten(
      lessonsQuestions.map(question => [
        `${question.lessonsquestion_id}_score`,
      ]),
    );
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  };
  const submitForm = () => {
  };
  const sortCategories = R.sortWith([
    R.ascend(R.prop('lessons_category_order')),
  ]);
  const sortQuestions = R.sortWith([
    R.ascend(R.prop('lessons_question_order')),
  ]);
  const sortedCategories = sortCategories(lessonsCategories);
  const initialValues = {};
  if (source) {
    return (
      <div className={classes.root}>
        {permissions.isLoggedIn && permissions.canAccess && source.isPlayerViewAvailable && (
          <Button
            color="secondary"
            variant="outlined"
            component={Link}
            to={`/lessons/${source.type}/${source.id}?user=${source.finalUserId}&preview=false`}
            style={{
              position: 'absolute',
              top: 20,
              right: 20,
            }}
          >
            {t('Switch to player mode')}
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
                message={t(
                  `No lessons learned categories in this ${source.type} yet.`,
                )}
              />
            </div>
          )}
        </div>
        <Form
          keepDirtyOnReinitialize
          initialValues={initialValues}
          onSubmit={submitForm}
          validate={validate}
        >
          {({ handleSubmit }) => (
            <form id="lessonsAnswersForm" onSubmit={handleSubmit}>
              {sortedCategories.map((category) => {
                const questions = sortQuestions(
                  lessonsQuestions.filter(
                    n => n.lessons_question_category === category.lessonscategory_id,
                  ),
                );
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
                                {question.lessons_question_explanation
                                  || t('No explanation')}
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
                                {t(
                                  'Your overall evaluation about this question.',
                                )}
                              </Typography>
                              <SliderField
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
              <div style={{
                margin: '50px auto',
                textAlign: 'center',
              }}
              >
                <Button color="secondary" variant="contained" disabled>
                  {t('Submit')}
                </Button>
              </div>
            </form>
          )}
        </Form>
      </div>
    );
  }
  return <Loader />;
};

export default LessonsPreview;
