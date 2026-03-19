import { CastForEducationOutlined, HelpOutlined } from '@mui/icons-material';
import { Box, Chip, LinearProgress, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { truncate } from '../../../../utils/String';
import { LessonContext, PermissionsContext } from '../../common/Context';
import LessonsCategoryAddTeams from '../categories/LessonsCategoryAddTeams';
import LessonsCategoryPopover from '../categories/LessonsCategoryPopover';
import CreateLessonsQuestion from '../categories/questions/CreateLessonsQuestion';
import LessonsQuestionPopover from '../categories/questions/LessonsQuestionPopover';

const useStyles = makeStyles()(() => ({ chip: { margin: '0 10px 10px 0' } }));

const LessonsCategories = ({
  lessonsCategories,
  lessonsAnswers,
  lessonsQuestions,
  setSelectedQuestion = {},
  teamsMap,
  teams,
  isReport,
  style = {},
}) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);

  // Context
  const { onUpdateLessonsCategoryTeams } = useContext(LessonContext);

  const sortCategories = R.sortWith([
    R.ascend(R.prop('lessons_category_order')),
  ]);
  const sortQuestions = R.sortWith([
    R.ascend(R.prop('lessons_question_order')),
  ]);
  const sortedCategories = sortCategories(lessonsCategories);
  const handleUpdateTeams = (lessonsCategoryId, teamsIds) => {
    const data = { lessons_category_teams: teamsIds };
    return onUpdateLessonsCategoryTeams(lessonsCategoryId, data);
  };
  const consolidatedAnswers = R.pipe(
    R.groupBy(R.prop('lessons_answer_question')),
    R.toPairs,
    R.map(([key, values]) => {
      const totalScore = R.sum(R.map(o => o.lessons_answer_score, values));
      return [
        key,
        {
          score: Math.round(totalScore / values.length), // Calculate average directly
          number: values.length,
          comments: R.filter(
            o => o.lessons_answer_positive !== null || o.lessons_answer_negative !== null,
            values,
          ).length,
        },
      ];
    }),
    R.fromPairs,
  )(lessonsAnswers);
  return (
    <div
      id="lessons_categories"
      style={{
        display: 'grid',
        gap: `${theme.spacing(2)} 0`,
        gridTemplateColumns: '1fr',
        ...style,
      }}
    >
      {sortedCategories.map((category) => {
        const questions = sortQuestions(
          lessonsQuestions.filter(
            n => n.lessons_question_category === category.lessonscategory_id,
          ),
        );
        return (
          <div key={category.lessonscategory_id}>
            <Typography variant="h2">
              {category.lessons_category_name}
              {!isReport && permissions.canManage && (
                <LessonsCategoryPopover
                  lessonsCategory={category}
                />
              )}
            </Typography>
            <div style={{
              display: 'grid',
              gap: `0 ${theme.spacing(3)}`,
              gridTemplateColumns: '1fr 1fr 1fr',
            }}
            >
              <Typography variant="h4">{t('Questions')}</Typography>
              <Typography variant="h4">{t('Results')}</Typography>
              <Typography variant="h4">
                {t('Targeted teams')}
                {!isReport && permissions.canManage && (
                  <LessonsCategoryAddTeams
                    lessonsCategoryId={category.lessonscategory_id}
                    lessonsCategoryTeamsIds={category.lessons_category_teams}
                    handleUpdateTeams={handleUpdateTeams}
                    teams={teams}
                    teamsMap={teamsMap}
                  />
                )}
              </Typography>
              <Paper variant="outlined">
                <List style={{ padding: 0 }}>
                  {questions.map(question => (
                    <ListItem
                      key={question.lessonsquestion_id}
                      divider
                      secondaryAction={!isReport && permissions.canManage && (
                        <LessonsQuestionPopover
                          lessonsCategoryId={category.lessonscategory_id}
                          lessonsQuestion={question}
                        />
                      )}
                    >
                      <ListItemIcon>
                        <HelpOutlined />
                      </ListItemIcon>
                      <ListItemText
                        style={{ width: '50%' }}
                        primary={question.lessons_question_content}
                        secondary={question.lessons_question_explanation || t('No explanation')}
                      />
                    </ListItem>
                  ))}
                  {!isReport && permissions.canManage && (
                    <CreateLessonsQuestion
                      inline
                      lessonsCategoryId={category.lessonscategory_id}
                    />
                  )}
                </List>
              </Paper>
              <Paper variant="outlined">
                <List style={{ padding: 0 }}>
                  {questions.map((question) => {
                    const consolidatedAnswer = consolidatedAnswers[
                      question.lessonsquestion_id
                    ] || {
                      score: 0,
                      number: 0,
                      comments: 0,
                    };
                    return (
                      <ListItemButton
                        key={question.lessonsquestion_id}
                        divider
                        onClick={() => setSelectedQuestion && setSelectedQuestion(question)}
                      >
                        <ListItemText
                          style={{ width: '50%' }}
                          primary={`${consolidatedAnswer.number} ${t(
                            'answers',
                          )}`}
                          secondary={`${t('of which')} ${
                            consolidatedAnswer.comments
                          } ${t('contain comments')}`}
                        />
                        <Box
                          sx={{
                            display: 'flex',
                            alignItems: 'center',
                            width: '30%',
                            marginRight: 1,
                          }}
                        >
                          <Box sx={{
                            width: '100%',
                            mr: 1,
                          }}
                          >
                            <LinearProgress
                              variant="determinate"
                              value={consolidatedAnswer.score}
                            />
                          </Box>
                          <Box sx={{ minWidth: 35 }}>
                            <Typography
                              variant="body2"
                              color="text.secondary"
                            >
                              {consolidatedAnswer.score}
                              %
                            </Typography>
                          </Box>
                        </Box>
                      </ListItemButton>
                    );
                  })}
                </List>
              </Paper>
              <Paper variant="outlined" style={{ padding: theme.spacing(2) }}>
                {category.lessons_category_teams.map((teamId) => {
                  const team = teamsMap[teamId];
                  return (
                    <Tooltip
                      key={teamId}
                      title={team?.team_name || ''}
                    >
                      {permissions.canManage ? (

                        <Chip
                          onDelete={
                            isReport
                              ? undefined
                              : () => handleUpdateTeams(
                                  category.lessonscategory_id,
                                  R.filter(
                                    n => n !== teamId,
                                    category.lessons_category_teams,
                                  ),
                                )
                          }
                          label={truncate(team?.team_name || '', 30)}
                          icon={<CastForEducationOutlined />}
                          classes={{ root: classes.chip }}
                        />
                      ) : (
                        <Chip
                          label={truncate(team?.team_name || '', 30)}
                          icon={<CastForEducationOutlined />}
                          classes={{ root: classes.chip }}
                        />
                      )}
                    </Tooltip>
                  );
                })}
              </Paper>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default LessonsCategories;
