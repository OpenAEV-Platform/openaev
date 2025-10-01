import { type Exercise, type Report } from '../../utils/api-types';

export interface ReportsHelper {
  getExerciseReports: (exerciseId: Exercise['exercise_id']) => Report[];
  getReport: (reportId: Report['report_id']) => Report;
}
