import { Checkbox, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import * as R from 'ramda';
import { CSSProperties, FunctionComponent, useContext, useMemo, useState } from 'react';
import * as React from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import type { InjectorContractConvertedContent, InjectOutputType, InjectStore } from '../../../../actions/injects/Inject';
import ChainedTimeline from '../../../../components/ChainedTimeline';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import { buildEmptyFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import ItemBoolean from '../../../../components/ItemBoolean';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import PlatformIcon from '../../../../components/PlatformIcon';
import {
  Article,
  FilterGroup,
  Inject,
  InjectBulkUpdateOperation,
  InjectInput,
  InjectTestStatusOutput,
  Team,
  Variable,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import useEntityToggle from '../../../../utils/hooks/useEntityToggle';
import { splitDuration } from '../../../../utils/Time';
import { isNotEmptyField } from '../../../../utils/utils';
import { InjectContext, PermissionsContext, ViewModeContext } from '../Context';
import ToolBar from '../ToolBar';
import CreateInject from './CreateInject';
import InjectIcon from './InjectIcon';
import InjectorContract from './InjectorContract';
import InjectPopover from './InjectPopover';
import InjectsListButtons from './InjectsListButtons';
import UpdateInject from './UpdateInject';

const useStyles = makeStyles()(() => ({
  disabled: {
    opacity: 0.38,
    pointerEvents: 'none',
  },
  duration: {
    fontSize: 12,
    lineHeight: '12px',
    height: 20,
    float: 'left',
    marginRight: 7,
    borderRadius: 4,
    width: 180,
    backgroundColor: 'rgba(0, 177, 255, 0.08)',
    color: '#00b1ff',
    border: '1px solid #00b1ff',
  },
  itemHead: {
    textTransform: 'uppercase',
  },
  item: {
    height: 50,
  },
  bodyItems: {
    display: 'flex',
  },
  bodyItem: {
    height: 20,
    fontSize: 13,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    paddingRight: 10,
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  inject_type: {
    width: '15%',
  },
  inject_title: {
    width: '25%',
  },
  inject_depends_duration: {
    width: '18%',
  },
  inject_platforms: {
    width: '10%',
  },
  inject_enabled: {
    width: '12%',
  },
  inject_tags: {
    width: '20%',
  },
};

interface Props {
  exerciseOrScenarioId: string;
  setViewMode?: (mode: string) => void;
  availableButtons: string[];
  teams: Team[];
  articles: Article[];
  variables: Variable[];
  uriVariable: string;
  allUsersNumber?: number;
  usersNumber?: number;
  teamsUsers: never;
}

const Injects: FunctionComponent<Props> = ({
  exerciseOrScenarioId,
  setViewMode,
  availableButtons,
  teams,
  articles,
  variables,
  uriVariable,
  allUsersNumber,
  usersNumber,
  teamsUsers,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t, tPick } = useFormatter();
  const injectContext = useContext(InjectContext);
  const viewModeContext = useContext(ViewModeContext);
  const { permissions } = useContext(PermissionsContext);

  // Headers
  const headers = useMemo(() => [
    {
      field: 'inject_type',
      label: 'Type',
      isSortable: false,
      value: (_: InjectOutputType, injectContract: InjectorContractConvertedContent) => {
        const injectorContractName = tPick(injectContract?.label);
        return injectContract
          ? (
              <InjectorContract
                variant="list"
                config={injectContract?.config}
                label={injectorContractName}
              />
            )
          : <InjectorContract variant="list" label={t('Deleted')} deleted />;
      },
    },
    {
      field: 'inject_title',
      label: 'Title',
      isSortable: true,
      value: (inject: InjectOutputType, _: InjectorContractConvertedContent) => <>{inject.inject_title}</>,
    },
    {
      field: 'inject_depends_duration',
      label: 'Trigger',
      isSortable: true,
      value: (inject: InjectOutputType, _: InjectorContractConvertedContent) => {
        const duration = splitDuration(
          inject.inject_depends_duration || 0,
        );
        return (
          <Chip
            classes={{ root: classes.duration }}
            label={`${duration.days}
                          ${t('d')}, ${duration.hours}
                          ${t('h')}, ${duration.minutes}
                          ${t('m')}`}
          />
        );
      },
    },
    {
      field: 'inject_platforms',
      label: 'Platform(s)',
      isSortable: false,
      value: (inject: InjectOutputType, _: InjectorContractConvertedContent) => (
        <>
          {
            inject.inject_injector_contract?.injector_contract_platforms?.map(
              platform => (
                <PlatformIcon
                  key={platform}
                  width={20}
                  platform={platform}
                  marginRight={10}
                />
              ),
            )
          }
        </>
      ),
    },
    {
      field: 'inject_enabled',
      label: 'Status',
      isSortable: false,
      value: (inject: InjectOutputType, _: InjectorContractConvertedContent) => {
        let injectStatus = inject.inject_enabled
          ? t('Enabled')
          : t('Disabled');
        if (!inject.inject_ready) {
          injectStatus = t('Missing content');
        }
        return (
          <ItemBoolean
            status={inject.inject_ready
              ? inject.inject_enabled : false}
            label={injectStatus}
            variant="inList"
            tooltip={injectStatus}
          />
        );
      },
    },
    {
      field: 'inject_tags',
      label: 'Tags',
      isSortable: false,
      value: (inject: InjectOutputType, _: InjectorContractConvertedContent) => <ItemTags variant="list" tags={inject.inject_tags} />,
    },
  ], []);

  // Filters
  const availableFilterNames = [
    'inject_platforms',
    'inject_kill_chain_phases',
    'inject_injector_contract',
    'inject_type',
    'inject_title',
  ];

  const quickFilter: FilterGroup = {
    mode: 'and',
    filters: [
      buildEmptyFilter('inject_platforms', 'contains'),
      buildEmptyFilter('inject_kill_chain_phases', 'contains'),
      buildEmptyFilter('inject_injector_contract', 'contains'),
    ],
  };

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(`${exerciseOrScenarioId}-injects`, buildSearchPagination({
    sorts: initSorting('inject_depends_duration', 'ASC'),
    filterGroup: quickFilter,
    size: 20,
  }));

  // Injects
  // scoped to page
  const [injects, setInjects] = useState<InjectOutputType[]>([]);
  // Bulk loading indcator for tests and delete
  const [isBulkLoading, setIsBulkLoading] = useState<boolean>(false);
  const [selectedInjectId, setSelectedInjectId] = useState<string | null>(null);
  const [reloadInjectCount, setReloadInjectCount] = useState(0);

  // Optimistic update
  const onCreate = (result: { result: string; entities: { injects: Record<string, InjectStore> } }) => {
    if (result.entities) {
      const created = result.entities.injects[result.result];
      setInjects([created as InjectOutputType, ...injects]);
      queryableHelpers.paginationHelpers.handleChangeTotalElements(queryableHelpers.paginationHelpers.getTotalElements() + 1);
    }
  };

  const onUpdate = (result: { result: string; entities: { injects: Record<string, InjectStore> } }) => {
    if (result.entities) {
      const updatedResults = result.entities.injects[result.result];
      setInjects(injects.map(i => i.inject_id !== updatedResults.inject_id ? i : updatedResults as InjectOutputType));
    }
  };

  const onBulkUpdate = (updatedResults: Inject[]) => {
    setInjects(injects.map((originalInject) => {
      return updatedResults.find(updatedInject => updatedInject.inject_id === originalInject.inject_id) as unknown as InjectOutputType || originalInject;
    }));
  };

  const onDelete = (result: string) => {
    if (result) {
      setInjects(injects.filter(i => (i.inject_id !== result)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(queryableHelpers.paginationHelpers.getTotalElements() - 1);
    }
  };

  const onCreateInject = async (data: InjectInput) => {
    await injectContext.onAddInject(data as Inject).then((result: { result: string; entities: { injects: Record<string, InjectStore> } }) => {
      onCreate(result);
    });
  };

  const onUpdateInject = async (data: Inject) => {
    if (selectedInjectId) {
      await injectContext.onUpdateInject(selectedInjectId, data).then((result: { result: string; entities: { injects: Record<string, InjectStore> } }) => {
        onUpdate(result);
        return result;
      });
    }
  };

  const massUpdateInject = async (data: Inject[]) => {
    const promises: Promise<InjectStore | undefined>[] = [];
    data.forEach((inject) => {
      promises.push(injectContext.onUpdateInject(inject.inject_id, inject).then((result: { result: string; entities: { injects: Record<string, InjectStore> } }) => {
        if (result.entities) {
          return result.entities.injects[result.result];
        }
        return undefined;
      }));
    });

    Promise.all(promises).then((values) => {
      if (values !== undefined) {
        const updatedInjects = injects
          .map(inject => (values.find(value => value !== undefined && value.inject_id === inject.inject_id)
            ? (values.find(value => value !== undefined && value?.inject_id === inject.inject_id) as InjectOutputType)
            : inject as InjectOutputType));
        setInjects(updatedInjects);
      }
    });
  };

  const [openCreateDrawer, setOpenCreateDrawer] = useState(false);

  const [presetInjectDuration, setPresetInjectDuration] = useState<number>(0);
  const openCreateInjectDrawer = (duration: number) => {
    setOpenCreateDrawer(true);
    setPresetInjectDuration(duration);
  };

  // Toolbar
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<InjectOutputType>('inject', injects, queryableHelpers.paginationHelpers.getTotalElements());
  const onRowShiftClick = (currentIndex: number, currentEntity: { inject_id: string }, event: React.SyntheticEvent | null = null) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    if (selectedElements && !R.isEmpty(selectedElements)) {
      // Find the indexes of the first and last selected entities
      let firstIndex = R.findIndex(
        (n: Inject) => n.inject_id === R.head(R.values(selectedElements)).inject_id,
        injects,
      );
      if (currentIndex > firstIndex) {
        let entities: InjectOutputType[] = [];
        while (firstIndex <= currentIndex) {
          entities = [...entities, injects[firstIndex]];

          firstIndex++;
        }
        const forcedRemove = R.values(selectedElements).filter(
          (n: Inject) => !entities.map(o => o.inject_id).includes(n.inject_id),
        );
        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
        // @ts-expect-error
        return onToggleEntity(entities, event, forcedRemove);
      }
      let entities: InjectOutputType[] = [];
      while (firstIndex >= currentIndex) {
        entities = [...entities, injects[firstIndex]];

        firstIndex--;
      }
      const forcedRemove = R.values(selectedElements).filter(
        (n: Inject) => !entities.map(o => o.inject_id).includes(n.inject_id),
      );
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-expect-error
      return onToggleEntity(entities, event, forcedRemove);
    }
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-expect-error
    return onToggleEntity(currentEntity, event);
  };

  const injectIdsToProcess = (selectAll: boolean) => {
    return selectAll
      ? []
      : Object.keys(selectedElements).filter(k => !Object.keys(deSelectedElements).includes(k));
  };

  const injectIdsToIgnore = (selectAll: boolean) => {
    return selectAll
      ? Object.keys(deSelectedElements)
      : Object.keys(deSelectedElements).filter(k => !Object.keys(selectedElements).includes(k));
  };

  const massUpdateInjects = async (actions: {
    field: string;
    type: string;
    values: { value: string }[];
  }[]) => {
    const operationsToPerform: InjectBulkUpdateOperation[] = [];
    for (const action of actions) {
      // Case where no values where given
      if (!action.values.length || !action.values[0].value) continue;
      operationsToPerform.push({
        operation: action.type.toLowerCase(),
        field: action.field,
        values: R.uniq(action.values.map(n => n.value)),
      } as InjectBulkUpdateOperation); // Cast is necessary because typeof enum don't work with operation and fields
    }
    await injectContext.onBulkUpdateInject({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      inject_ids_to_process: selectAll ? undefined : injectIdsToProcess(selectAll),
      inject_ids_to_ignore: injectIdsToIgnore(selectAll),
      simulation_or_scenario_id: exerciseOrScenarioId,
      update_operations: operationsToPerform,
    })
      .then((result) => {
        if (result) onBulkUpdate(result);
      });
  };

  const bulkDeleteInjects = () => {
    setIsBulkLoading(true);
    const deleteIds = injectIdsToProcess(selectAll);
    const ignoreIds = injectIdsToIgnore(selectAll);
    injectContext.onBulkDeleteInjects({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      inject_ids_to_process: selectAll ? undefined : deleteIds,
      inject_ids_to_ignore: ignoreIds,
      simulation_or_scenario_id: exerciseOrScenarioId,
    }).then((result) => {
      // We update the numbers of elements in the pagination
      const newNumbers = Math.max(0, (queryableHelpers.paginationHelpers.getTotalElements() - result.length));
      // We remove the deleted injects from the current data table
      const deletedIds = result.map(inject => inject.inject_id);
      setInjects(newNumbers !== 0 ? injects.filter(inject => !deletedIds.includes(inject.inject_id)) : []);
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newNumbers);
    }).finally(() => {
      setIsBulkLoading(false);
    });
  };

  const massTestInjects = () => {
    setIsBulkLoading(true);
    const testIds = injectIdsToProcess(selectAll);
    const ignoreIds = injectIdsToIgnore(selectAll);
    injectContext.bulkTestInjects({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      inject_ids_to_process: selectAll ? undefined : testIds,
      inject_ids_to_ignore: ignoreIds,
      simulation_or_scenario_id: exerciseOrScenarioId,
    }).then((result: { uri: string; data: InjectTestStatusOutput[] }) => {
      if (numberOfSelectedElements === 1) {
        MESSAGING$.notifySuccess(t('Inject test has been sent, you can view test logs details on {itsDedicatedPage}.', {
          itsDedicatedPage: <Link to={`${result.uri}/${result.data[0].status_id}`}>{t('its dedicated page')}</Link>,
        }));
      } else {
        MESSAGING$.notifySuccess(t('Inject test has been sent, you can view test logs details on {itsDedicatedPage}.', {
          itsDedicatedPage: <Link to={`${result.uri}`}>{t('its dedicated page')}</Link>,
        }));
      }
    }).finally(() => {
      setIsBulkLoading(false);
    });
  };

  const selectedInjects = () => {
    return Object.values(selectedElements);
  };

  const atLeastOneValidInject = injects.some(inject => !inject.inject_injector_contract?.injector_contract_content_parsed);

  if (isBulkLoading) {
    return <Loader />;
  }
  return (
    <>
      <PaginationComponentV2
        fetch={input => injectContext.searchInjects(input)}
        searchPaginationInput={searchPaginationInput}
        setContent={setInjects}
        entityPrefix="inject"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        reloadContentCount={reloadInjectCount}
        topBarButtons={(
          <InjectsListButtons
            selectedInjects={selectedInjects()}
            availableButtons={availableButtons}
            setViewMode={setViewMode}
            onImportedInjects={() => setReloadInjectCount(prev => prev + 1)}
            isAtLeastOneValidInject={atLeastOneValidInject}
          />
        )}
      />
      {viewModeContext === 'chain' && (
        <div style={{ marginBottom: 10 }}>
          <div>
            <ChainedTimeline
              injects={injects}
              exerciseOrScenarioId={exerciseOrScenarioId}
              onUpdateInject={massUpdateInject}
              onTimelineClick={openCreateInjectDrawer}
              onSelectedInject={(inject) => {
                const injectContract = inject?.inject_injector_contract.convertedContent;
                const isContractExposed = injectContract?.config.expose;
                if (injectContract && isContractExposed) {
                  setSelectedInjectId(inject?.inject_id);
                }
              }}
              onCreate={onCreate}
              onUpdate={onUpdate}
              onDelete={onDelete}
            />
            <div className="clearfix" />
          </div>
        </div>
      )}
      {viewModeContext === 'list' && (
        <List>
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
            secondaryAction={<>&nbsp;</>}
          >
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={selectAll}
                disableRipple
                onChange={handleToggleSelectAll}
                disabled={typeof handleToggleSelectAll !== 'function'}
              />
            </ListItemIcon>
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
          {injects.map((inject: InjectOutputType, index) => {
            const injectContract = inject.inject_injector_contract?.convertedContent;
            const isContractExposed = injectContract?.config.expose;
            return (
              <ListItem
                key={inject.inject_id}
                divider
                classes={{ root: classes.item }}
                secondaryAction={(
                  <InjectPopover
                    inject={inject}
                    exerciseOrScenarioId={exerciseOrScenarioId}
                    canBeTested
                    setSelectedInjectId={setSelectedInjectId}
                    isDisabled={!injectContract || !isContractExposed}
                    onCreate={onCreate}
                    onUpdate={onUpdate}
                    onDelete={onDelete}
                  />
                )}
                disablePadding
              >
                <ListItemButton
                  onClick={() => {
                    if (injectContract && isContractExposed) {
                      setSelectedInjectId(inject.inject_id);
                    }
                  }}
                >
                  <ListItemIcon
                    style={{ minWidth: 40 }}
                    onClick={event => (event.shiftKey
                      ? onRowShiftClick(index, inject, event)
                      : onToggleEntity(inject, event))}
                  >
                    <Checkbox
                      edge="start"
                      checked={
                        (selectAll && !(inject.inject_id
                          in (deSelectedElements || {})))
                          || inject.inject_id in (selectedElements || {})
                      }
                      disableRipple
                    />
                  </ListItemIcon>
                  <ListItemIcon style={{ paddingTop: 5 }}>
                    <InjectIcon
                      isPayload={isNotEmptyField(inject.inject_injector_contract?.injector_contract_payload)}
                      type={
                        inject.inject_injector_contract?.injector_contract_payload
                          ? inject.inject_injector_contract?.injector_contract_payload?.payload_collector_type
                          || inject.inject_injector_contract?.injector_contract_payload?.payload_type
                          : inject.inject_type
                      }
                      disabled={!injectContract || !isContractExposed || !inject.inject_enabled}
                    />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div className={(!injectContract || !isContractExposed
                        || !inject.inject_enabled) ? classes.disabled : ''}
                      >
                        <div className={classes.bodyItems}>
                          {headers.map(header => (
                            <div
                              key={header.field}
                              className={classes.bodyItem}
                              style={inlineStyles[header.field]}
                            >
                              {header.value(inject, injectContract)}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            );
          })}
        </List>
      )}
      {permissions.canWrite && (
        <>
          {selectedInjectId !== null
          && (
            <UpdateInject
              open
              handleClose={() => setSelectedInjectId(null)}
              onUpdateInject={onUpdateInject}
              massUpdateInject={massUpdateInject}
              injectId={selectedInjectId}
              // @ts-expect-error typing
              articlesFromExerciseOrScenario={articles}
              variablesFromExerciseOrScenario={variables}
              exerciseOrScenarioId={exerciseOrScenarioId}
              uriVariable={uriVariable}
              allUsersNumber={allUsersNumber}
              usersNumber={usersNumber}
              teamsUsers={teamsUsers}
              injects={injects}
            />
          )}
          <ButtonCreate onClick={() => {
            setOpenCreateDrawer(true);
            setPresetInjectDuration(0);
          }}
          />
          <ToolBar
            numberOfSelectedElements={numberOfSelectedElements}
            totalNumberOfElements={queryableHelpers.paginationHelpers.getTotalElements()}
            selectedElements={selectedElements}
            deSelectedElements={deSelectedElements}
            selectAll={selectAll}
            handleClearSelectedElements={handleClearSelectedElements}
            teamsFromExerciseOrScenario={teams}
            id={exerciseOrScenarioId}
            handleUpdate={massUpdateInjects}
            handleBulkDelete={bulkDeleteInjects}
            handleBulkTest={massTestInjects}
          />
          <CreateInject
            title={t('Create a new inject')}
            open={openCreateDrawer}
            handleClose={() => setOpenCreateDrawer(false)}
            onCreateInject={onCreateInject}
            presetInjectDuration={presetInjectDuration}
            // @ts-expect-error typing
            teamsFromExerciseOrScenario={teams}
            articlesFromExerciseOrScenario={articles}
            variablesFromExerciseOrScenario={variables}
            uriVariable={uriVariable}
            allUsersNumber={allUsersNumber}
            usersNumber={usersNumber}
            teamsUsers={teamsUsers}
          />
        </>
      )}
    </>
  );
};

export default Injects;
