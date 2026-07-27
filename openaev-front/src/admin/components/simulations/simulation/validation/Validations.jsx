import { FactCheckOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useState } from 'react';
import { useDispatch } from 'react-redux';
import { useParams } from 'react-router';

import { fetchExerciseInjectExpectations } from '../../../../../actions/Exercise';
import { fetchExerciseInjects } from '../../../../../actions/Inject';
import { SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import Loader from '../../../../../components/Loader';
import SearchFilter from '../../../../../components/SearchFilter';
import { useHelper } from '../../../../../store';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { isNotEmptyField } from '../../../../../utils/utils';
import TagsFilter from '../../../common/filters/TagsFilter';
import InjectIcon from '../../../common/injects/InjectIcon';
import ExecutionMenu from '../ExecutionMenu';
import TeamOrAssetLine from './common/TeamOrAssetLine';

const cellStyle = {
  height: '100%',
  float: 'left',
  fontSize: 13,
};

const Validations = () => {
  const theme = useTheme();
  const dispatch = useDispatch();
  const { exerciseId } = useParams();
  const [tags, setTags] = useState([]);
  const { t, fndt } = useFormatter();
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
    const injectEntries = Object.entries(groupedByInject);
    return (
      <div>
        <ExecutionMenu exerciseId={exerciseId} />
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          paddingBottom: 5,
        }}
        >
          {/* Scoping toolbar, same anatomy as the other Execution screens. */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: theme.spacing(1.5),
          }}
          >
            <SearchFilter
              variant="small"
              onChange={handleSearch}
              keyword={keyword}
            />
            <TagsFilter
              onAddTag={handleAddTag}
              onRemoveTag={handleRemoveTag}
              currentTags={tags}
            />
          </div>
          <SectionBlock title={t('Manual validations')} disablePadding>
            {injectEntries.length === 0 ? (
              <Empty
                icon={FactCheckOutlined}
                message={t('No manual validations yet')}
                hint={t('Manual expectations will appear here when injects require human review')}
              />
            ) : (
              <List sx={{ padding: 0 }}>
                {injectEntries.map(([injectId, expectationsByInject]) => {
                  const inject = injectsMap[injectId] || {};
                  const injectContract = inject.inject_injector_contract.convertedContent || {};
                  return (
                    <div key={inject.inject_id}>
                      <ListItem divider={true} sx={{ height: 40 }}>
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
                              <div style={{
                                ...cellStyle,
                                width: '55%',
                                fontWeight: 600,
                              }}
                              >
                                {inject.inject_title}
                              </div>
                              <div style={{
                                ...cellStyle,
                                width: '15%',
                              }}
                              >
                                {fndt(inject.inject_sent_at)}
                              </div>
                              <div style={{
                                ...cellStyle,
                                width: '30%',
                              }}
                              >
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
            )}
          </SectionBlock>
        </Box>
      </div>
    );
  }
  return (
    <div>
      <ExecutionMenu exerciseId={exerciseId} />
      <Loader />
    </div>
  );
};

export default Validations;
