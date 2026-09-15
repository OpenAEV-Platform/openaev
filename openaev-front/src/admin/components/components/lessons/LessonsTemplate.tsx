import { Paper } from '@filigran/design-system';
import { SchoolOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type UserHelper } from '../../../../actions/helper';
import { fetchLessonsTemplateCategories, fetchLessonsTemplateQuestions } from '../../../../actions/Lessons';
import { type LessonsTemplatesHelper } from '../../../../actions/lessons/lesson-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type LessonsTemplateCategory, type LessonsTemplateQuestion } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CreateLessonsTemplateCategory from './categories/CreateLessonsTemplateCategory';
import LessonsTemplateCategoryCard from './categories/LessonsTemplateCategoryCard';

// Zero-state rendered when the template has no category yet: a dashed framed
// invitation card with the create CTA, instead of an empty grid.
const LessonsTemplateEmptyState = ({ lessonsTemplateId }: { lessonsTemplateId: string }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const accent = theme.palette.primary.main;
  return (
    <Paper
      padding={32}
      style={{
        borderStyle: 'dashed',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 12,
        textAlign: 'center',
      }}
    >
      <Box sx={{
        width: 48,
        height: 48,
        borderRadius: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: 'primary.main',
        backgroundColor: alpha(accent, 0.1),
        border: `1px solid ${alpha(accent, 0.3)}`,
      }}
      >
        <SchoolOutlined />
      </Box>
      <Typography sx={{
        fontFamily: '"Geologica", sans-serif',
        fontWeight: 600,
        fontSize: 16,
      }}
      >
        {t('No categories yet')}
      </Typography>
      <Typography
        variant="body2"
        sx={{
          color: 'text.secondary',
          maxWidth: 480,
        }}
      >
        {t('Structure this template with categories, then add the questions participants will answer after a simulation.')}
      </Typography>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
        <Box sx={{ marginTop: 1 }}>
          <CreateLessonsTemplateCategory
            lessonsTemplateId={lessonsTemplateId}
            label={t('Add a category')}
          />
        </Box>
      </Can>
    </Paper>
  );
};

const LessonsTemplate = () => {
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
  const categoriesSorted = [...categories]
    .sort((c1, c2) => ((c1.lessons_template_category_order ?? 0) > (c2.lessons_template_category_order ?? 0) ? 1 : -1));
  const sortQuestions = (qs: LessonsTemplateQuestion[]) => {
    return [...qs]
      .sort((q1, q2) => ((q1.lessons_template_question_order ?? 0) > (q2.lessons_template_question_order ?? 0) ? 1 : -1));
  };

  if (categoriesSorted.length === 0) {
    return (
      <Box sx={{ marginTop: 2 }}>
        <LessonsTemplateEmptyState lessonsTemplateId={lessonsTemplateId} />
      </Box>
    );
  }

  return (
    <Box sx={{
      marginTop: 2,
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fill, minmax(min(480px, 100%), 1fr))',
      gap: 2,
      alignItems: 'stretch',
    }}
    >
      {categoriesSorted.map((category, index) => (
        <LessonsTemplateCategoryCard
          key={category.lessonstemplatecategory_id}
          lessonsTemplateId={lessonsTemplateId}
          category={category}
          questions={sortQuestions(
            questions.filter(question => question.lessons_template_question_category === category.lessonstemplatecategory_id),
          )}
          index={index}
        />
      ))}
    </Box>
  );
};

export default LessonsTemplate;
