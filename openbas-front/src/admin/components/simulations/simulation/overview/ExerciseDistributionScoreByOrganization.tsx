import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type OrganizationHelper, type UserHelper } from '../../../../../actions/helper';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type InjectExpectation, type Organization } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { computeOrganizationsColors } from './DistributionUtils';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionScoreByOrganization: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectExpectations, organizations, organizationsMap, usersMap } = useHelper((helper: InjectHelper & OrganizationHelper & UserHelper) => ({
    injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
    organizationsMap: helper.getOrganizationsMap(),
    usersMap: helper.getUsersMap(),
    organizations: helper.getOrganizations(),
  }));

  const organizationsTotalScores = R.pipe(
    R.filter(
      (n: InjectExpectation) => !R.isEmpty(n.inject_expectation_results)
        && n.inject_expectation_user !== null,
    ),
    R.map((n: InjectExpectation) => R.assoc(
      'inject_expectation_user',
      n.inject_expectation_user ? usersMap[n.inject_expectation_user] : {},
      n,
    )),
    R.groupBy(R.path(['inject_expectation_user', 'user_organization'])),
    R.toPairs,
    R.map((n: [string, InjectExpectation[]]) => ({
      ...organizationsMap[n[0]],
      organization_total_score: R.sum(
        R.map((o: InjectExpectation) => o.inject_expectation_score, n[1]),
      ),
    })),
  )(injectExpectations);

  const sortedOrganizationsByTotalScore = R.pipe(
    R.sortWith([R.descend(R.prop('organization_total_score'))]),
    R.take(10),
  )(organizationsTotalScores);

  const organizationsColors = computeOrganizationsColors(organizations, theme);

  const totalScoreByOrganizationData = [
    {
      name: t('Total score'),
      data: sortedOrganizationsByTotalScore.map((o: Organization & { organization_total_score: number }) => ({
        x: o.organization_name ?? '',
        y: o.organization_total_score || null,
        fillColor: organizationsColors[o.organization_id] ?? '',
      })),
    },
  ];

  return (
    <>
      {organizationsTotalScores.length > 0 ? (
        <Chart
          id="exercise_distribution_total_score_by_organization"
          options={horizontalBarsChartOptions({ theme })}
          series={totalScoreByOrganizationData}
          type="bar"
          width="100%"
          height={50 + organizationsTotalScores.length * 50}
        />
      ) : (
        <Empty
          id="exercise_distribution_total_score_by_organization"
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};

export default ExerciseDistributionScoreByOrganization;
