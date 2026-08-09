import { CategoryOutlined, HelpOutlined, SchoolOutlined } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useParams } from 'react-router';

import { type UserHelper } from '../../../../actions/helper';
import { type LessonsTemplatesHelper } from '../../../../actions/lessons/lesson-helper';
import { DetailHero, HeroStat } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type LessonsTemplate, type LessonsTemplateCategory, type LessonsTemplateQuestion } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CreateLessonsTemplateCategory from './categories/CreateLessonsTemplateCategory';
import LessonsTemplatePopover from './LessonsTemplatePopover';

// Lessons learned template header, aligned on the shared DetailHero used by
// every other entity detail page. Carries the headline stats (categories /
// questions) and the "Add a category" CTA so the page content below can start
// immediately with the categories themselves.
const LessonsTemplateHeader = () => {
  const { t } = useFormatter();
  const { lessonsTemplateId } = useParams() as { lessonsTemplateId: string };
  const {
    lessonsTemplate,
    categories,
    questions,
  }: {
    lessonsTemplate: LessonsTemplate;
    categories: LessonsTemplateCategory[];
    questions: LessonsTemplateQuestion[];
  } = useHelper((helper: LessonsTemplatesHelper & UserHelper) => ({
    lessonsTemplate: helper.getLessonsTemplate(lessonsTemplateId),
    categories: helper.getLessonsTemplateCategories(lessonsTemplateId),
    questions: helper.getLessonsTemplateQuestions(),
  }));
  const categoryIds = new Set(categories.map(category => category.lessonstemplatecategory_id));
  const questionCount = questions.filter(question => question.lessons_template_question_category
    && categoryIds.has(question.lessons_template_question_category)).length;
  return (
    <DetailHero
      icon={SchoolOutlined}
      overline={t('Lessons learned template')}
      title={lessonsTemplate.lessons_template_name}
      action={(
        <>
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
            <CreateLessonsTemplateCategory
              lessonsTemplateId={lessonsTemplateId}
              label={t('Add a category')}
            />
          </Can>
          <LessonsTemplatePopover lessonsTemplate={lessonsTemplate} />
        </>
      )}
      stats={(
        <>
          <HeroStat
            icon={CategoryOutlined}
            label={t('Categories')}
            value={categories.length}
          />
          <HeroStat
            icon={HelpOutlined}
            label={t('Questions')}
            value={questionCount}
          />
        </>
      )}
      footer={lessonsTemplate.lessons_template_description
        ? (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {lessonsTemplate.lessons_template_description}
            </Typography>
          )
        : undefined}
    />
  );
};

export default LessonsTemplateHeader;
