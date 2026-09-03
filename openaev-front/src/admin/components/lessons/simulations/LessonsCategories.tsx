import { BallotOutlined, CastForEducationOutlined, HelpOutlined } from '@mui/icons-material';
import { Box, Chip, LinearProgress, List, ListItem, ListItemButton, ListItemText, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, type Theme, useTheme } from '@mui/material/styles';
import { type SystemStyleObject } from '@mui/system';
import { type FunctionComponent, useContext } from 'react';

import { SECTION_LABEL_SX } from '../../../../components/common/detail/detailStyles';
import { useFormatter } from '../../../../components/i18n';
import { type LessonsAnswer, type LessonsCategory, type LessonsQuestion, type Team } from '../../../../utils/api-types';
import { truncate } from '../../../../utils/String';
import { LessonContext, PermissionsContext } from '../../common/Context';
import LessonsCategoryAddTeams from '../categories/LessonsCategoryAddTeams';
import LessonsCategoryPopover from '../categories/LessonsCategoryPopover';
import CreateLessonsQuestion from '../categories/questions/CreateLessonsQuestion';
import LessonsQuestionPopover from '../categories/questions/LessonsQuestionPopover';

interface ConsolidatedAnswer {
  score: number;
  number: number;
  comments: number;
}

interface Props {
  lessonsCategories: LessonsCategory[];
  lessonsAnswers: LessonsAnswer[];
  lessonsQuestions: LessonsQuestion[];
  setSelectedQuestion?: (question: LessonsQuestion) => void;
  teamsMap: Record<string, Team>;
  teams: Team[];
  isReport?: boolean;
  style?: SystemStyleObject<Theme>;
}

// One block per category: a title row followed by a responsive three-column
// grid (questions / consolidated results / targeted teams), all rendered with
// the shared detail-page section anatomy.
const LessonsCategories: FunctionComponent<Props> = ({
  lessonsCategories,
  lessonsAnswers,
  lessonsQuestions,
  setSelectedQuestion,
  teamsMap,
  teams,
  isReport,
  style = {},
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);

  // Context
  const { onUpdateLessonsCategoryTeams } = useContext(LessonContext);

  const sortedCategories = [...lessonsCategories].sort(
    (a, b) => (a.lessons_category_order ?? 0) - (b.lessons_category_order ?? 0),
  );
  const handleUpdateTeams = (lessonsCategoryId: string, teamsIds: string[]) => {
    const data = { lessons_category_teams: teamsIds };
    return onUpdateLessonsCategoryTeams(lessonsCategoryId, data);
  };
  const answersByQuestion = lessonsAnswers.reduce<Record<string, LessonsAnswer[]>>(
    (acc, answer) => {
      const questionAnswers = acc[answer.lessons_answer_question] ?? [];
      questionAnswers.push(answer);
      acc[answer.lessons_answer_question] = questionAnswers;
      return acc;
    },
    {},
  );
  const consolidatedAnswers: Record<string, ConsolidatedAnswer> = Object.fromEntries(
    Object.entries(answersByQuestion).map(([key, values]) => {
      const totalScore = values.reduce((sum, o) => sum + o.lessons_answer_score, 0);
      return [
        key,
        {
          score: Math.round(totalScore / values.length), // Calculate average directly
          number: values.length,
          comments: values.filter(
            o => o.lessons_answer_positive !== null || o.lessons_answer_negative !== null,
          ).length,
        },
      ];
    }),
  );
  return (
    <Box
      id="lessons_categories"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        ...style,
      }}
    >
      {sortedCategories.map((category) => {
        const questions = lessonsQuestions
          .filter(n => n.lessons_question_category === category.lessonscategory_id)
          .sort((a, b) => (a.lessons_question_order ?? 0) - (b.lessons_question_order ?? 0));
        // The API types the team list as optional: default it once so the column renders its empty
        // state instead of throwing, as it would have before.
        const categoryTeams = category.lessons_category_teams ?? [];
        return (
          <section key={category.lessonscategory_id}>
            <header style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(1),
              marginBottom: theme.spacing(1.5),
            }}
            >
              <Typography
                component="h2"
                sx={{
                  fontFamily: theme.typography.h1.fontFamily,
                  fontSize: 16,
                  fontWeight: 600,
                  margin: 0,
                }}
              >
                {category.lessons_category_name}
              </Typography>
              <span style={{
                padding: '1px 6px',
                borderRadius: 2,
                backgroundColor: alpha(theme.palette.text.primary, 0.06),
                fontSize: 11,
                fontWeight: 500,
                fontVariantNumeric: 'tabular-nums',
                color: theme.palette.text.secondary,
              }}
              >
                {questions.length}
              </span>
              {!isReport && permissions.canManage && (
                <LessonsCategoryPopover lessonsCategory={category} />
              )}
              <div style={{
                flex: 1,
                height: 1,
                backgroundColor: alpha(theme.palette.text.primary, 0.05),
              }}
              />
            </header>
            <Box sx={{
              display: 'grid',
              gap: 2,
              gridTemplateColumns: {
                xs: 'minmax(0, 1fr)',
                md: 'repeat(3, minmax(0, 1fr))',
              },
              alignItems: 'stretch',
            }}
            >
              <div style={{
                display: 'flex',
                flexDirection: 'column',
              }}
              >
                <Typography sx={SECTION_LABEL_SX}>{t('Questions')}</Typography>
                <Paper
                  variant="outlined"
                  sx={{
                    borderRadius: 1,
                    flex: 1,
                    overflow: 'hidden',
                  }}
                >
                  <List disablePadding>
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
                        <Box
                          sx={{
                            width: 30,
                            height: 30,
                            borderRadius: 1,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            flexShrink: 0,
                            marginRight: 1.5,
                            color: 'primary.main',
                            backgroundColor: alpha(theme.palette.primary.main, 0.1),
                          }}
                        >
                          <HelpOutlined sx={{ fontSize: 16 }} />
                        </Box>
                        <ListItemText
                          primary={question.lessons_question_content}
                          secondary={question.lessons_question_explanation || t('No explanation')}
                          primaryTypographyProps={{
                            sx: {
                              fontSize: 13.5,
                              fontWeight: 600,
                            },
                          }}
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
              </div>
              <div style={{
                display: 'flex',
                flexDirection: 'column',
              }}
              >
                <Typography sx={SECTION_LABEL_SX}>{t('Results')}</Typography>
                <Paper
                  variant="outlined"
                  sx={{
                    borderRadius: 1,
                    flex: 1,
                    overflow: 'hidden',
                  }}
                >
                  <List disablePadding>
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
                          <Box
                            sx={{
                              width: 30,
                              height: 30,
                              borderRadius: 1,
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              flexShrink: 0,
                              marginRight: 1.5,
                              color: 'primary.main',
                              backgroundColor: alpha(theme.palette.primary.main, 0.1),
                            }}
                          >
                            <BallotOutlined sx={{ fontSize: 16 }} />
                          </Box>
                          <ListItemText
                            sx={{ width: '50%' }}
                            primary={`${consolidatedAnswer.number} ${t('answers')}`}
                            secondary={`${t('of which')} ${consolidatedAnswer.comments} ${t('contain comments')}`}
                            primaryTypographyProps={{
                              sx: {
                                fontSize: 13.5,
                                fontWeight: 600,
                              },
                            }}
                          />
                          <Box
                            sx={{
                              display: 'flex',
                              alignItems: 'center',
                              width: '30%',
                              flexShrink: 0,
                              marginRight: 1,
                              gap: 1,
                            }}
                          >
                            <LinearProgress
                              variant="determinate"
                              value={consolidatedAnswer.score}
                              sx={{
                                flex: 1,
                                borderRadius: 1,
                              }}
                            />
                            <Typography
                              variant="body2"
                              sx={{
                                minWidth: 35,
                                color: 'text.secondary',
                                fontVariantNumeric: 'tabular-nums',
                              }}
                            >
                              {consolidatedAnswer.score}
                              %
                            </Typography>
                          </Box>
                        </ListItemButton>
                      );
                    })}
                  </List>
                </Paper>
              </div>
              <div style={{
                display: 'flex',
                flexDirection: 'column',
              }}
              >
                <Typography sx={SECTION_LABEL_SX}>
                  {t('Targeted teams')}
                  {!isReport && permissions.canManage && (
                    <LessonsCategoryAddTeams
                      lessonsCategoryId={category.lessonscategory_id}
                      lessonsCategoryTeamsIds={categoryTeams}
                      handleUpdateTeams={handleUpdateTeams}
                      teams={teams}
                    />
                  )}
                </Typography>
                <Paper
                  variant="outlined"
                  sx={{
                    padding: 2,
                    borderRadius: 1,
                    flex: 1,
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 1,
                    alignContent: 'flex-start',
                  }}
                >
                  {categoryTeams.length === 0 && (
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                      {t('No targeted teams')}
                    </Typography>
                  )}
                  {categoryTeams.map((teamId) => {
                    const team = teamsMap[teamId];
                    return (
                      <Tooltip key={teamId} title={team?.team_name || ''}>
                        <Chip
                          onDelete={
                            isReport || !permissions.canManage
                              ? undefined
                              : () => handleUpdateTeams(
                                  category.lessonscategory_id,
                                  categoryTeams.filter(n => n !== teamId),
                                )
                          }
                          label={truncate(team?.team_name || '', 30)}
                          icon={<CastForEducationOutlined />}
                          sx={{ borderRadius: 1 }}
                        />
                      </Tooltip>
                    );
                  })}
                </Paper>
              </div>
            </Box>
          </section>
        );
      })}
    </Box>
  );
};

export default LessonsCategories;
