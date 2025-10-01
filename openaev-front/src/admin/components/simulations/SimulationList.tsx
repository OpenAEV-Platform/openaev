import { HubOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type ReactNode, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchExercisesGlobalScores } from '../../../actions/exercises/exercise-action';
import { type QueryableHelpers } from '../../../components/common/queryable/QueryableHelpers';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { type Header } from '../../../components/common/SortHeadersList';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import ItemTargets from '../../../components/ItemTargets';
import Loader from '../../../components/Loader';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { type ExercisesGlobalScoresOutput, type ExerciseSimple, type ExpectationResultsByType } from '../../../utils/api-types';
import AtomicTestingResult from '../atomic_testings/atomic_testing/AtomicTestingResult';
import ExerciseStatus from './simulation/ExerciseStatus';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const getInlineStyles = (variant: string): Record<string, CSSProperties> => ({
  exercise_name: { width: variant === 'reduced-view' ? '15%' : '15%' },
  exercise_start_date: { width: variant === 'reduced-view' ? '12%' : '13%' },
  exercise_status: { width: variant === 'reduced-view' ? '12%' : '10%' },
  exercise_targets: {
    width: variant === 'reduced-view' ? '15%' : '17%',
    cursor: 'default',
  },
  exercise_global_score: {
    width: variant === 'reduced-view' ? '18%' : '12%',
    cursor: 'default',
  },
  exercise_tags: {
    width: variant === 'reduced-view' ? '12%' : '17%',
    cursor: 'default',
  },
  exercise_updated_at: { width: variant === 'reduced-view' ? '12%' : '13%' },
});

function getGlobalScoreComponent(
  exercise: ExerciseSimple,
) {
  return (<AtomicTestingResult expectations={exercise.exercise_global_score} />);
}

function getGlobalScoreComponentAsync(
  exercise: ExerciseSimple,
  loadingGlobalScores: boolean,
  globalScores: Record<string, ExpectationResultsByType[]> | undefined,
) {
  return (
    <>
      {(loadingGlobalScores) && <Loader variant="inElement" size="xs" />}
      {(!loadingGlobalScores && globalScores) && <AtomicTestingResult expectations={globalScores[exercise.exercise_id]} />}
    </>
  );
}

interface Props {
  exercises: ExerciseSimple[];
  queryableHelpers?: QueryableHelpers;
  hasHeader?: boolean;
  variant?: string;
  secondaryAction?: (exercise: ExerciseSimple) => ReactNode;
  loading: boolean;
  isGlobalScoreAsync?: boolean;
}

const SimulationList: FunctionComponent<Props> = ({
  exercises = [],
  queryableHelpers,
  hasHeader = true,
  variant = 'list',
  secondaryAction,
  loading,
  isGlobalScoreAsync = false,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const inlineStyles = getInlineStyles(variant);
  const { nsdt, vnsdt } = useFormatter();

  const [loadingGlobalScores, setLoadingGlobalScores] = useState(true);
  const [globalScores, setGlobalScores] = useState<Record<string, ExpectationResultsByType[]>>();
  const fetchGlobalScores = (exerciseIds: string[]) => {
    setLoadingGlobalScores(true);
    fetchExercisesGlobalScores({ exercise_ids: exerciseIds })
      .then((result: { data: ExercisesGlobalScoresOutput }) => setGlobalScores(result.data.global_scores_by_exercise_ids))
      .finally(() => setLoadingGlobalScores(false));
  };

  useEffect(() => {
    if (exercises.length > 0) {
      fetchGlobalScores(exercises.map(exercise => exercise.exercise_id));
    }
  }, [exercises]);

  // Headers
  const headers: Header[] = [
    {
      field: 'exercise_name',
      label: 'Name',
      isSortable: true,
      value: (exercise: ExerciseSimple) => <>{exercise.exercise_name}</>,
    },
    {
      field: 'exercise_start_date',
      label: 'Start date',
      isSortable: true,
      value: (exercise: ExerciseSimple) => {
        if (!exercise.exercise_start_date) {
          return '-';
        }
        return <>{(variant === 'reduced-view' ? vnsdt(exercise.exercise_start_date) : nsdt(exercise.exercise_start_date))}</>;
      },
    },
    {
      field: 'exercise_status',
      label: 'Status',
      isSortable: true,
      value: (exercise: ExerciseSimple) => <ExerciseStatus variant="list" exerciseStartDate={exercise.exercise_start_date} exerciseStatus={exercise.exercise_status} />,
    },
    {
      field: 'exercise_targets',
      label: 'Target',
      isSortable: false,
      value: (exercise: ExerciseSimple) => <ItemTargets variant={variant} targets={exercise.exercise_targets} />,
    },
    {
      field: 'exercise_global_score',
      label: 'Global score',
      isSortable: false,
      value: (exercise: ExerciseSimple) => isGlobalScoreAsync ? getGlobalScoreComponentAsync(exercise, loadingGlobalScores, globalScores) : getGlobalScoreComponent(exercise),
    },
    {
      field: 'exercise_tags',
      label: 'Tags',
      isSortable: false,
      value: (exercise: ExerciseSimple) => <ItemTags variant={variant} tags={exercise.exercise_tags} />,
    },
    {
      field: 'exercise_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (exercise: ExerciseSimple) => {
        if (!exercise.exercise_updated_at) {
          return '-';
        }
        return <>{(variant === 'reduced-view' ? vnsdt(exercise.exercise_updated_at) : nsdt(exercise.exercise_updated_at))}</>;
      },
    },
  ];

  return (
    <List>
      {hasHeader && queryableHelpers
        && (
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
            secondaryAction={<>&nbsp;</>}
          >
            <ListItemIcon />
            <ListItemText
              primary={(
                <SortHeadersComponentV2
                  headers={headers}
                  inlineStylesHeaders={inlineStyles}
                  sortHelpers={queryableHelpers.sortHelpers}
                />
              )}
            />
          </ListItem>
        )}
      {
        loading
          ? <PaginatedListLoader Icon={HubOutlined} headers={headers} headerStyles={inlineStyles} />
          : exercises.map((exercise: ExerciseSimple) => (
              <ListItem
                key={exercise.exercise_id}
                secondaryAction={secondaryAction && secondaryAction(exercise)}
                disablePadding
                divider
              >
                <ListItemButton
                  classes={{ root: classes.item }}
                  component={Link}
                  to={`/admin/simulations/${exercise.exercise_id}`}
                >
                  <ListItemIcon>
                    <HubOutlined color="primary" />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        {headers.map(header => (
                          <div
                            key={header.field}
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles[header.field],
                            }}
                          >
                            {header.value?.(exercise)}
                          </div>
                        ))}
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))
      }
    </List>
  );
};

export default SimulationList;
