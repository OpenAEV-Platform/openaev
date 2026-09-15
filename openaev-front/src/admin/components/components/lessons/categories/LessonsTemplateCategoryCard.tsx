import { Paper } from '@filigran/design-system';
import { Box, Chip, List, ListItem, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type LessonsTemplateCategory, type LessonsTemplateQuestion } from '../../../../../utils/api-types';
import { Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import LessonsTemplateCategoryPopover from './LessonsTemplateCategoryPopover';
import CreateLessonsTemplateQuestion from './questions/CreateLessonsTemplateQuestion';
import LessonsTemplateQuestionPopover from './questions/LessonsTemplateQuestionPopover';

interface Props {
  lessonsTemplateId: string;
  category: LessonsTemplateCategory;
  questions: LessonsTemplateQuestion[];
  /** Zero-based position of the category in the ordered template. */
  index: number;
}

// One lessons learned category rendered as a marketplace-style card: an
// accent-tinted header band carrying the order badge, name, question count and
// description, followed by the ordered list of questions and the inline
// "add question" row.
const LessonsTemplateCategoryCard: FunctionComponent<Props> = ({
  lessonsTemplateId,
  category,
  questions,
  index,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const accent = theme.palette.primary.main;
  const hairline = alpha(theme.palette.text.primary, 0.05);
  return (
    <Paper
      padding={0}
      data-testid="lessons-category-card"
      style={{
        // `overflow: hidden` is carried over on purpose: without it the first
        // child's gradient band overflows the surface's rounded corner.
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 1.5,
        padding: 2,
        background: `linear-gradient(135deg, ${alpha(accent, 0.07)}, transparent 65%)`,
        borderBottom: `1px solid ${hairline}`,
      }}
      >
        <Box sx={{
          width: 36,
          height: 36,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
          color: 'primary.main',
          backgroundColor: alpha(accent, 0.1),
          border: `1px solid ${alpha(accent, 0.3)}`,
          fontFamily: '"Geologica", sans-serif',
          fontWeight: 500,
          fontSize: 15,
        }}
        >
          {index + 1}
        </Box>
        <Box sx={{
          flex: 1,
          minWidth: 0,
        }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            minHeight: 24,
          }}
          >
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontWeight: 600,
              fontSize: 15,
              lineHeight: 1.3,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            >
              {category.lessons_template_category_name}
            </Typography>
            <Chip
              label={(() => {
                if (questions.length === 1) return t('1 question');
                return t('{count} questions', { count: questions.length });
              })()}
              size="small"
              sx={{
                height: 18,
                fontSize: 11,
                borderRadius: 0.5,
                flexShrink: 0,
                color: 'text.secondary',
              }}
            />
          </Box>
          {category.lessons_template_category_description && (
            <Typography
              variant="body2"
              sx={{
                color: 'text.secondary',
                marginTop: 0.25,
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {category.lessons_template_category_description}
            </Typography>
          )}
        </Box>
        <Box sx={{
          flexShrink: 0,
          marginTop: -0.5,
          marginRight: -1,
        }}
        >
          <LessonsTemplateCategoryPopover
            lessonsTemplateId={lessonsTemplateId}
            lessonsTemplateCategory={category}
          />
        </Box>
      </Box>
      <List
        disablePadding
        sx={{ flex: 1 }}
      >
        {questions.map((question, questionIndex) => {
          const explanation = question.lessons_template_question_explanation?.trim();
          return (
            <ListItem
              key={question.lessonstemplatequestion_id}
              divider
              secondaryAction={(
                <LessonsTemplateQuestionPopover
                  lessonsTemplateId={lessonsTemplateId}
                  lessonsTemplateCategoryId={category.lessonstemplatecategory_id}
                  lessonsTemplateQuestion={question}
                />
              )}
              sx={{ paddingBlock: 1.25 }}
            >
              <ListItemIcon sx={{ minWidth: 42 }}>
                <Box sx={{
                  width: 26,
                  height: 26,
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  backgroundColor: alpha(accent, 0.08),
                  color: 'text.secondary',
                  fontFamily: '"Geologica", sans-serif',
                  fontWeight: 500,
                  fontSize: 11,
                }}
                >
                  {`Q${questionIndex + 1}`}
                </Box>
              </ListItemIcon>
              <ListItemText
                primary={(
                  <Typography sx={{
                    fontSize: 13.5,
                    fontWeight: 500,
                    lineHeight: 1.35,
                  }}
                  >
                    {question.lessons_template_question_content}
                  </Typography>
                )}
                secondary={(
                  <Typography
                    variant="body2"
                    sx={{
                      fontSize: 12,
                      color: 'text.secondary',
                      marginTop: 0.25,
                      ...(explanation ? {} : { fontStyle: 'italic' }),
                    }}
                  >
                    {explanation || t('No explanation')}
                  </Typography>
                )}
                disableTypography
              />
            </ListItem>
          );
        })}
        {questions.length === 0 && (
          <ListItem divider sx={{ paddingBlock: 1.25 }}>
            <ListItemText
              primary={(
                <Typography sx={{
                  fontSize: 13,
                  fontStyle: 'italic',
                  color: 'text.secondary',
                }}
                >
                  {t('No questions yet in this category.')}
                </Typography>
              )}
              disableTypography
            />
          </ListItem>
        )}
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
          <CreateLessonsTemplateQuestion
            lessonsTemplateId={lessonsTemplateId}
            lessonsTemplateCategoryId={category.lessonstemplatecategory_id}
          />
        </Can>
      </List>
    </Paper>
  );
};

export default LessonsTemplateCategoryCard;
