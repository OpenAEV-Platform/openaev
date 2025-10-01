import { CastForEducationOutlined, HelpOutlined } from '@mui/icons-material';
import { Chip, List, ListItem, ListItemIcon, ListItemText, Paper, Tooltip, Typography } from '@mui/material';
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
  lessonsQuestions,
  teamsMap,
  teams,
  isReport,
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
  return (
    <div style={{
      display: 'grid',
      gap: `${theme.spacing(2)} 0`,
      gridTemplateColumns: '1fr',
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
              gridTemplateColumns: '3fr 2fr',
            }}
            >
              <Typography variant="h4" style={{ alignContent: 'center' }}>{t('Questions')}</Typography>
              <div>
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
              </div>
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
                          onDelete={isReport
                            ? undefined
                            : () => handleUpdateTeams(
                                category.lessonscategory_id,
                                R.filter(
                                  n => n !== teamId,
                                  category.lessons_category_teams,
                                ),
                              )}
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
