import { SchoolOutlined } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useParams } from 'react-router';

import { type UserHelper } from '../../../../actions/helper';
import { type LessonsTemplatesHelper } from '../../../../actions/lessons/lesson-helper';
import { DetailHero } from '../../../../components/common/detail/EntityDetailCommon';
import { useHelper } from '../../../../store';
import LessonsTemplatePopover from './LessonsTemplatePopover';

// Lessons learned template header, aligned on the shared DetailHero used by
// every other entity detail page (same icon box, title style and kebab sizing).
const LessonsTemplateHeader = () => {
  const { lessonsTemplateId } = useParams() as { lessonsTemplateId: string };
  const { lessonsTemplate } = useHelper((helper: LessonsTemplatesHelper & UserHelper) => ({ lessonsTemplate: helper.getLessonsTemplate(lessonsTemplateId) }));
  return (
    <DetailHero
      icon={SchoolOutlined}
      title={lessonsTemplate.lessons_template_name}
      action={<LessonsTemplatePopover lessonsTemplate={lessonsTemplate} />}
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
