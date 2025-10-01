import { type LessonsTemplate, type LessonsTemplateCategory, type LessonsTemplateQuestion } from '../../utils/api-types';

export interface LessonsTemplatesHelper {
  getLessonsTemplate: (lessonsTemplateId: LessonsTemplate['lessonstemplate_id']) => LessonsTemplate;
  getLessonsTemplateCategories: (lessonsTemplateId: LessonsTemplate['lessonstemplate_id']) => LessonsTemplateCategory[];
  getLessonsTemplateQuestions: () => LessonsTemplateQuestion[];
  getLessonsTemplates: () => LessonsTemplate[];
}
