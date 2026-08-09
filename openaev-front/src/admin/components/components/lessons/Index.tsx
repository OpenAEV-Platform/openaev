import { Route, Routes, useParams } from 'react-router';

import { fetchLessonsTemplates } from '../../../../actions/Lessons';
import { type LessonsTemplatesHelper } from '../../../../actions/lessons/lesson-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { LESSONS_TEMPLATES_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import CustomizationMenu from '../../settings/CustomizationMenu';
import LessonsTemplate from './LessonsTemplate';
import LessonsTemplateHeader from './LessonsTemplateHeader';

const Index = () => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const { lessonsTemplateId } = useParams() as { lessonsTemplateId: string };
  const { lessonsTemplate } = useHelper((helper: LessonsTemplatesHelper) => ({ lessonsTemplate: helper.getLessonsTemplate(lessonsTemplateId) }));
  useDataLoader(() => {
    dispatch(fetchLessonsTemplates());
  });

  if (lessonsTemplate) {
    return (
      <div style={{ display: 'flex' }}>
        <div style={{
          flexGrow: 1,
          minWidth: 0,
        }}
        >
          <Breadcrumbs
            variant="object"
            elements={[
              { label: t(SETTINGS_LABEL) },
              { label: t('Customization') },
              {
                label: t('Lessons learned'),
                link: LESSONS_TEMPLATES_BASE_URL,
              },
              {
                label: lessonsTemplate.lessons_template_name,
                current: true,
              },
            ]}
          />
          <LessonsTemplateHeader />
          <Routes>
            <Route path="" element={errorWrapper(LessonsTemplate)()} />
            {/* Not found */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </div>
        <CustomizationMenu />
      </div>
    );
  }
  return <Loader />;
};

export default Index;
