import { Paper } from '@filigran/design-system';
import { BallotOutlined, CastForEducationOutlined, HelpOutlined } from '@mui/icons-material';
import { Box, Chip, LinearProgress, List, ListItem, ListItemButton, ListItemText, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useContext } from 'react';

import { SECTION_LABEL_SX } from '../../../../components/common/detail/detailStyles';
import { useFormatter } from '../../../../components/i18n';
import { truncate } from '../../../../utils/String';
import { LessonContext, PermissionsContext } from '../../common/Context';
import LessonsCategoryAddTeams from '../categories/LessonsCategoryAddTeams';
import LessonsCategoryPopover from '../categories/LessonsCategoryPopover';
import CreateLessonsQuestion from '../categories/questions/CreateLessonsQuestion';
import LessonsQuestionPopover from '../categories/questions/LessonsQuestionPopover';

// One block per category: a title row followed by a responsive three-column
// grid (questions / consolidated results / targeted teams), all rendered with
// the shared detail-page section anatomy.
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
        const questions = sortQuestions(
          lessonsQuestions.filter(
            n => n.lessons_question_category === category.lessonscategory_id,
          ),
        );
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
                {/* padding=0 (iso): row dividers stay edge to edge — PAPER-GAP-INVENTORY §5.5. */}
                <Paper
                  padding={0}
                  style={{
                    flex: 1,
                    overflow: 'hidden',
                  }}
                >
                  <List
                    disablePadding
                    // The last row's divider lands 1px above the Paper's own border and
                    // reads as a 2px line (measured: 1px divider, 1px gap, 1px border).
                    // Only the last child loses it — intermediate rows keep theirs.
                    sx={{ '& > :last-child': { borderBottom: 0 } }}
                  >
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
                {/* padding=0 (iso): row dividers stay edge to edge — PAPER-GAP-INVENTORY §5.5. */}
                <Paper
                  padding={0}
                  style={{
                    flex: 1,
                    overflow: 'hidden',
                  }}
                >
                  <List
                    disablePadding
                    // The last row's divider lands 1px above the Paper's own border and
                    // reads as a 2px line (measured: 1px divider, 1px gap, 1px border).
                    // Only the last child loses it — intermediate rows keep theirs.
                    sx={{ '& > :last-child': { borderBottom: 0 } }}
                  >
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
                      lessonsCategoryTeamsIds={category.lessons_category_teams}
                      handleUpdateTeams={handleUpdateTeams}
                      teams={teams}
                      teamsMap={teamsMap}
                    />
                  )}
                </Typography>
                {/* padding=16 (iso): the chips' own padding is intrinsic and stays — PAPER-GAP-INVENTORY §5.3. */}
                <Paper
                  padding={16}
                  style={{
                    flex: 1,
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 8,
                    alignContent: 'flex-start',
                  }}
                >
                  {category.lessons_category_teams.length === 0 && (
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                      {t('No targeted teams')}
                    </Typography>
                  )}
                  {category.lessons_category_teams.map((teamId) => {
                    const team = teamsMap[teamId];
                    return (
                      <Tooltip key={teamId} title={team?.team_name || ''}>
                        <Chip
                          onDelete={
                            isReport || !permissions.canManage
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
