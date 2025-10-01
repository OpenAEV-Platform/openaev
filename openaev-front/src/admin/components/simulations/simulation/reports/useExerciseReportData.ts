import { type Dispatch, type SetStateAction, useEffect, useState } from 'react';

import { fetchExercise, fetchExerciseTeams } from '../../../../../actions/Exercise';
import {
  exerciseInjectsResultOutput,
  fetchExerciseExpectationResult,
  fetchLessonsAnswers,
  fetchLessonsCategories,
  fetchLessonsQuestions,
  fetchPlayersByExercise,
} from '../../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { type UserHelper } from '../../../../../actions/helper';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { fetchReportFromSimulation } from '../../../../../actions/reports/report-actions';
import { type ReportsHelper } from '../../../../../actions/reports/report-helper';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import { useHelper } from '../../../../../store';
import {
  type Exercise,
  type ExpectationResultsByType,
  type InjectResultOutput,
  type LessonsAnswer,
  type LessonsCategory,
  type LessonsQuestion,
  type Report,
  type ReportInformation,
  type Team,
  type User,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import ReportInformationType from './ReportInformationType';

export interface ExerciseReportData {
  injects: InjectResultOutput[];
  exerciseExpectationResults: ExpectationResultsByType[];
  exercise: Exercise;
  lessonsCategories: LessonsCategory[];
  lessonsQuestions: LessonsQuestion[];
  lessonsAnswers: LessonsAnswer[];
  teams: Team[];
  teamsMap: Record<string, Team>;
  usersMap: Record<string, User>;
}

interface ReturnType {
  loading: boolean;
  report: Report;
  displayModule: (moduleType: ReportInformationType) => boolean;
  setReloadReportDataCount: Dispatch<SetStateAction<number>>;
  reportData: ExerciseReportData;
}

const useExerciseReportData = (reportId: Report['report_id'], exerciseId: Exercise['exercise_id']): ReturnType => {
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState(true);
  const [reloadReportDataCount, setReloadReportDataCount] = useState(0);
  const [exerciseExpectationResults, setResults] = useState<ExpectationResultsByType[]>([]);
  const [injects, setInjects] = useState<InjectResultOutput[]>([]);

  const {
    report,
    exercise,
    lessonsCategories,
    lessonsQuestions,
    lessonsAnswers,
    teams,
    teamsMap,
    usersMap,
  } = useHelper((helper: InjectHelper & ReportsHelper & ExercisesHelper & TeamsHelper & UserHelper) => {
    return {
      report: helper.getReport(reportId),
      exercise: helper.getExercise(exerciseId),
      lessonsCategories: helper.getExerciseLessonsCategories(exerciseId),
      lessonsQuestions: helper.getExerciseLessonsQuestions(exerciseId),
      lessonsAnswers: helper.getExerciseLessonsAnswers(exerciseId),
      teamsMap: helper.getTeamsMap(),
      teams: helper.getExerciseTeams(exerciseId),
      usersMap: helper.getUsersMap(),
    };
  });

  const displayModule = (moduleType: ReportInformationType): boolean => {
    return report?.report_informations.find((info: ReportInformation) => info.report_informations_type === moduleType)?.report_informations_display;
  };

  useDataLoader(() => {
    dispatch(fetchReportFromSimulation(exerciseId, reportId)).then(() => {
      setReloadReportDataCount(prev => prev + 1);
    });
  });

  useEffect(() => {
    if (reloadReportDataCount > 0) {
      setLoading(true);
      const fetchPromises = [];
      fetchPromises.push(dispatch(fetchExercise(exerciseId)));
      fetchPromises.push(dispatch(fetchExerciseTeams(exerciseId)));
      if (displayModule(ReportInformationType.PLAYER_SURVEYS)) {
        fetchPromises.push(
          dispatch(fetchLessonsQuestions(exerciseId)),
          dispatch(fetchLessonsAnswers(exerciseId)),
          dispatch(fetchLessonsCategories(exerciseId)),
          dispatch(fetchPlayersByExercise(exerciseId)),
        );
      }
      if (displayModule(ReportInformationType.SCORE_DETAILS)) {
        fetchPromises.push(fetchExerciseExpectationResult(exerciseId).then(result => setResults(result.data)));
      }

      if (displayModule(ReportInformationType.INJECT_RESULT)) {
        fetchPromises.push(exerciseInjectsResultOutput(exerciseId).then((result) => {
          // Sort the injects by tracking_sent_date
          const sortedInjects = result.data.sort((a: InjectResultOutput, b: InjectResultOutput) => {
            const dateA = a.inject_status?.tracking_sent_date;
            const dateB = b.inject_status?.tracking_sent_date;
            if ((dateA === undefined || dateA === null) && (dateB !== undefined && dateB !== null)) return 1;
            if ((dateA !== undefined && dateA !== null) && (dateB === undefined || dateB === null)) return -1;
            if ((dateA === undefined || dateA === null) && (dateB === undefined || dateB === null)) return 0;
            return dateA!.localeCompare(dateB!); // non-null assertion since we've checked above
          });
          setInjects(sortedInjects);
        }));
      }
      Promise.all(fetchPromises).then(() => {
        setLoading(false);
      });
    }
  }, [reloadReportDataCount]);

  return {
    loading,
    report,
    displayModule,
    setReloadReportDataCount,
    reportData: {
      injects,
      exerciseExpectationResults,
      exercise,
      lessonsCategories,
      lessonsQuestions,
      lessonsAnswers,
      teams,
      teamsMap,
      usersMap,
    },
  };
};

export default useExerciseReportData;
