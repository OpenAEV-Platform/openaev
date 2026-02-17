import { RowingOutlined } from '@mui/icons-material';
import { Chip, List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText, Tooltip } from '@mui/material';
import { useContext } from 'react';
import { useDispatch } from 'react-redux';
import { Link, useLocation } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchChallenges } from '../../../../actions/challenge-action';
import { fetchDocuments } from '../../../../actions/Document';
import { fetchExercises } from '../../../../actions/Exercise';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext, Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import useSearchAndFilter from '../../../../utils/SortingFiltering';
import TagsFilter from '../../common/filters/TagsFilter';
import ChallengePopover from './ChallengePopover';
import CreateChallenge from './CreateChallenge';

const useStyles = makeStyles()(() => ({
  parameters: {
    marginTop: -10,
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    height: 52,
  },
  filters: {
    display: 'flex',
    gap: '10px',
  },
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
    height: 40,
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  exercise: {
    fontSize: 12,
    height: 20,
    float: 'left',
    marginRight: 7,
    width: 120,
  },
}));

const headerStyles = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  challenge_name: {
    width: '25%',
    fontSize: 12,
    fontWeight: '700',
  },
  challenge_category: {
    width: '20%',
    fontSize: 12,
    fontWeight: '700',
  },
  challenge_score: {
    width: '10%',
    fontSize: 12,
    fontWeight: '700',
  },
  challenge_exercises: {
    width: '20%',
    fontSize: 12,
    fontWeight: '700',
  },
  challenge_tags: {
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles = {
  challenge_name: { width: '25%' },
  challenge_category: { width: '20%' },
  challenge_score: { width: '10%' },
  challenge_exercises: { width: '20%' },
  challenge_tags: { height: 20 },
};

const Challenges = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const dispatch = useDispatch();
  const { t } = useFormatter();
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const initialKeyword = params.get('search') || '';
  const ability = useContext(AbilityContext);

  // Filter and sort hook
  const searchColumns = ['name', 'content', 'category'];
  const filtering = useSearchAndFilter('challenge', 'name', searchColumns, { defaultKeyword: initialKeyword });
  // Fetching data
  const { challenges, documentsMap, exercisesMap } = useHelper(helper => ({
    exercisesMap: helper.getExercisesMap(),
    challenges: helper.getChallenges(),
    documentsMap: helper.getDocumentsMap(),
  }));
  useDataLoader(() => {
    dispatch(fetchExercises());
    dispatch(fetchChallenges());
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)) {
      dispatch(fetchDocuments());
    }
  });
  const sortedChallenges = filtering.filterAndSort(challenges);
  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Components') }, {
          label: t('Challenges'),
          current: true,
        }]}
      />
      <div className={classes.parameters}>
        <div className={classes.filters}>
          <SearchFilter
            variant="small"
            onChange={filtering.handleSearch}
            keyword={filtering.keyword}
          />
          <TagsFilter
            onAddTag={filtering.handleAddTag}
            onRemoveTag={filtering.handleRemoveTag}
            currentTags={filtering.tags}
            tagsFetched
          />
        </div>
      </div>
      <div className="clearfix" />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
        >
          <ListItemIcon>
            <span
              style={{
                padding: '0 8px 0 8px',
                fontWeight: 700,
                fontSize: 12,
              }}
            >
              &nbsp;
            </span>
          </ListItemIcon>
          <ListItemText
            primary={(
              <div style={bodyItemsStyles.bodyItems}>
                {filtering.buildHeader(
                  'challenge_name',
                  'Name',
                  true,
                  headerStyles,
                )}
                {filtering.buildHeader(
                  'challenge_category',
                  'Category',
                  true,
                  headerStyles,
                )}
                {filtering.buildHeader(
                  'challenge_score',
                  'Score',
                  true,
                  headerStyles,
                )}
                {filtering.buildHeader(
                  'challenge_exercises',
                  'Simulations',
                  true,
                  headerStyles,
                )}
                {filtering.buildHeader(
                  'challenge_tags',
                  'Tags',
                  true,
                  headerStyles,
                )}
              </div>
            )}
          />
          <ListItemSecondaryAction>&nbsp;</ListItemSecondaryAction>
        </ListItem>
        {sortedChallenges.map((challenge) => {
          const docs = challenge.challenge_documents
            .map(d => (documentsMap[d] ? documentsMap[d] : undefined))
            .filter(d => d !== undefined);
          return (
            <ListItem
              key={challenge.challenge_id}
              classes={{ root: classes.item }}
              divider={true}
            >
              <ListItemIcon>
                <RowingOutlined color="primary" />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div style={bodyItemsStyles.bodyItems}>
                    <div
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.challenge_name,
                      }}
                    >
                      {challenge.challenge_name}
                    </div>
                    <div
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.challenge_category,
                      }}
                    >
                      {challenge.challenge_category}
                    </div>
                    <div
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.challenge_score,
                      }}
                    >
                      {challenge.challenge_score}
                    </div>
                    <div
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.challenge_exercises,
                      }}
                    >
                      {challenge.challenge_exercises && challenge.challenge_exercises.length > 0 ? (
                        challenge.challenge_exercises.slice(0, 3).map((e) => {
                          const exercise = exercisesMap[e];

                          if (!exercise) {
                            return <span key={e}>-</span>;
                          }

                          return (
                            <Tooltip
                              key={exercise.exercise_id}
                              title={exercise.exercise_name}
                            >
                              <Chip
                                icon={<RowingOutlined style={{ fontSize: 12 }} />}
                                classes={{ root: classes.exercise }}
                                variant="outlined"
                                label={exercise.exercise_name}
                                component={Link}
                                clickable
                                to={`/admin/simulations/${exercise.exercise_id}`}
                                onClick={(event) => {
                                  event.stopPropagation();
                                }}
                              />
                            </Tooltip>
                          );
                        })
                      ) : (
                        <span>-</span>
                      )}
                    </div>
                    <div
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.challenge_tags,
                      }}
                    >
                      <ItemTags
                        variant="list"
                        tags={challenge.challenge_tags}
                      />
                    </div>
                  </div>
                )}
              />
              <ListItemSecondaryAction>
                <ChallengePopover challenge={challenge} documents={docs} />
              </ListItemSecondaryAction>
            </ListItem>
          );
        })}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.CHALLENGES}>
        <CreateChallenge />
      </Can>
    </>
  );
};

export default Challenges;
