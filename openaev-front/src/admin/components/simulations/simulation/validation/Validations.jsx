import { List, ListItem, ListItemIcon, ListItemText, Slide } from '@mui/material';
import * as R from 'ramda';
import { forwardRef, useState } from 'react';
import { useDispatch } from 'react-redux';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchExerciseInjectExpectations } from '../../../../../actions/Exercise';
import { fetchExerciseInjects } from '../../../../../actions/Inject';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import Loader from '../../../../../components/Loader';
import SearchFilter from '../../../../../components/SearchFilter';
import { useHelper } from '../../../../../store';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { isNotEmptyField } from '../../../../../utils/utils';
import TagsFilter from '../../../common/filters/TagsFilter';
import InjectIcon from '../../../common/injects/InjectIcon';
import AnimationMenu from '../AnimationMenu';
import TeamOrAssetLine from './common/TeamOrAssetLine';

const Transition = forwardRef((props, ref) => (
  <Slide direction="up" ref={ref} {...props} />
));
Transition.displayName = 'TransitionSlide';

const useStyles = makeStyles()(() => ({
  item: { height: 40 },
  bodyItem: {
    height: '100%',
    float: 'left',
    fontSize: 13,
  },
}));

const Validations = () => {
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const { exerciseId } = useParams();
  const [tags, setTags] = useState([]);
  const { fndt } = useFormatter();
  const [keyword, setKeyword] = useState('');
  const handleSearch = value => setKeyword(value);
  const handleAddTag = (value) => {
    if (value) {
      setTags(R.uniq(R.append(value, tags)));
    }
  };
  const handleRemoveTag = value => setTags(R.filter(n => n.id !== value, tags));
  // Fetching data
  const {
    exercise,
    injectExpectations,
    injectsMap,
  } = useHelper((helper) => {
    return {
      exercise: helper.getExercise(exerciseId),
      injectsMap: helper.getInjectsMap(),
      injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
    };
  });
  useDataLoader(() => {
    dispatch(fetchExerciseInjectExpectations(exerciseId));
    dispatch(fetchExerciseInjects(exerciseId));
  });
  const filterByKeyword = n => keyword === ''
    || (n.inject_expectation_inject?.inject_title || '')
      .toLowerCase()
      .indexOf(keyword.toLowerCase()) !== -1
      || (n.inject_expectation_inject?.inject_description || '')
        .toLowerCase()
        .indexOf(keyword.toLowerCase()) !== -1;
  const sort = R.sortWith([R.descend(R.prop('inject_expectation_created_at'))]);
  const sortedInjectExpectations = R.pipe(
    R.uniqBy(R.prop('inject_expectation_id')),
    R.map(n => R.assoc(
      'inject_expectation_inject',
      injectsMap[n.inject_expectation_inject] || {},
      n,
    )),
    R.filter(n => n.inject_expectation_type === 'MANUAL'),
    R.filter(
      n => tags.length === 0
        || R.any(
          filter => R.includes(filter, n.inject_expectation_inject?.inject_tags),
          R.pluck('id', tags),
        ),
    ),
    R.filter(filterByKeyword),
    sort,
  )(injectExpectations);

  const groupedByInject = sortedInjectExpectations.reduce((group, expectation) => {
    const { inject_expectation_inject } = expectation;
    const { inject_id } = inject_expectation_inject;
    if (inject_id) {
      const values = group[inject_id] ?? [];
      values.push(expectation);
      group[inject_id] = values;
    }
    return group;
  }, {});

  const groupedByTeamOrAsset = (expectations) => {
    return expectations.reduce((group, expectation) => {
      const { inject_expectation_team } = expectation;
      const { inject_expectation_asset } = expectation;
      const { inject_expectation_asset_group } = expectation;
      if (inject_expectation_team) {
        const values = group[inject_expectation_team] ?? [];
        values.push(expectation);
        group[inject_expectation_team] = values;
      }
      if (inject_expectation_asset && !expectation.inject_expectation_group) {
        const values = group[inject_expectation_asset] ?? [];
        values.push(expectation);
        group[inject_expectation_asset] = values;
      }
      if (inject_expectation_asset_group) {
        const values = group[inject_expectation_asset_group] ?? [];
        values.push(expectation);
        group[inject_expectation_asset_group] = values;
      }
      return group;
    }, {});
  };

  // Rendering
  if (exercise && injectExpectations) {
    return (
      <div>
        <AnimationMenu exerciseId={exerciseId} />
        <div style={{
          float: 'left',
          marginRight: 10,
        }}
        >
          <SearchFilter
            variant="small"
            onChange={handleSearch}
            keyword={keyword}
          />
        </div>
        <div style={{
          float: 'left',
          marginRight: 10,
        }}
        >
          <TagsFilter
            onAddTag={handleAddTag}
            onRemoveTag={handleRemoveTag}
            currentTags={tags}
          />
        </div>
        <div className="clearfix" />
        <List>
          {Object.entries(groupedByInject).map(([injectId, expectationsByInject]) => {
            const inject = injectsMap[injectId] || {};
            const injectContract = inject.inject_injector_contract.convertedContent || {};
            return (
              <div key={inject.inject_id}>
                <ListItem divider={true} classes={{ root: classes.item }}>
                  <ListItemIcon style={{ paddingTop: 5 }}>
                    <InjectIcon
                      isPayload={isNotEmptyField(inject.inject_injector_contract.injector_contract_payload)}
                      type={
                        inject.inject_injector_contract.injector_contract_payload
                          ? inject.inject_injector_contract.injector_contract_payload?.payload_collector_type
                          || inject.inject_injector_contract.injector_contract_payload?.payload_type
                          : inject.inject_type
                      }
                      disabled={!inject.inject_enabled}
                      size="small"
                    />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <>
                        <div className={classes.bodyItem} style={{ width: '55%' }}>
                          {inject.inject_title}
                        </div>
                        <div className={classes.bodyItem} style={{ width: '15%' }}>
                          {fndt(inject.inject_sent_at)}
                        </div>
                        <div className={classes.bodyItem} style={{ width: '30%' }}>
                          <ItemTags variant="list" tags={inject.inject_tags} />
                        </div>
                      </>
                    )}
                  />
                </ListItem>
                <List component="div" disablePadding>
                  {Object.entries(groupedByTeamOrAsset(expectationsByInject)).map(([id, expectations]) => {
                    return (
                      <TeamOrAssetLine
                        key={id}
                        exerciseId={exerciseId}
                        inject={inject}
                        injectContract={injectContract}
                        expectationsByInject={expectationsByInject}
                        id={id}
                        expectations={expectations}
                      />
                    );
                  })}
                </List>
              </div>
            );
          })}
        </List>
      </div>
    );
  }
  return (
    <div className={classes.container}>
      <AnimationMenu exerciseId={exerciseId} />
      <Loader />
    </div>
  );
};

export default Validations;
