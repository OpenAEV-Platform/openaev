import { HelpOutlineOutlined, PersonOutlined } from '@mui/icons-material';
import { Box, Checkbox, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useContext, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type OrganizationHelper, type UserHelper } from '../../../actions/helper';
import { fetchOrganizations } from '../../../actions/Organization';
import { bulkDeletePlayers, searchPlayers } from '../../../actions/players/player-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ExportButton from '../../../components/common/ExportButton';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../components/common/SortHeadersList';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { PERSON_BASE_URL } from '../../../constants/BaseUrls';
import { useHelper } from '../../../store';
import { type PlayerOutput, type SearchPaginationInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import { AbilityContext, Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ToolBar from '../common/ToolBar';
import CreatePlayer from './players/CreatePlayer';
import PlayerPopover from './players/PlayerPopover';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  user_email: { width: '25%' },
  user_firstname: { width: '15%' },
  user_lastname: { width: '15%' },
  user_organization: {
    width: '20%',
    cursor: 'default',
  },
  user_tags: { width: '25%' },
};

const Players = () => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t } = useFormatter();

  // Fetching data
  const { organizationsMap } = useHelper((helper: UserHelper & OrganizationHelper) => ({ organizationsMap: helper.getOrganizationsMap() }));

  useDataLoader(() => {
    dispatch(fetchOrganizations());
  });

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'user_email',
      label: 'Email address',
      isSortable: true,
      value: (player: PlayerOutput) => player.user_email,
    },
    {
      field: 'user_firstname',
      label: 'Firstname',
      isSortable: true,
      value: (player: PlayerOutput) => player.user_firstname || '-',
    },
    {
      field: 'user_lastname',
      label: 'Lastname',
      isSortable: true,
      value: (player: PlayerOutput) => player.user_lastname || '-',
    },
    {
      field: 'user_organization',
      label: 'Organization',
      isSortable: false,
      value: (player: PlayerOutput) => (player.user_organization ? organizationsMap[player.user_organization]?.organization_name : '-'),
    },
    {
      field: 'user_tags',
      label: 'Tags',
      isSortable: true,
      value: (player: PlayerOutput) => <ItemTags variant="list" tags={player.user_tags} />,
    },
  ], [organizationsMap]);

  const availableFilterNames = [
    'user_email',
    'user_firstname',
    'user_lastname',
    'user_organization',
    'user_tags',
  ];

  const [players, setPlayers] = useState<PlayerOutput[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('players', buildSearchPagination({
    sorts: initSorting('user_email'),
    textSearch: search,
  }));

  // Export
  const exportProps = {
    exportType: 'user',
    exportKeys: [
      'user_email',
      'user_firstname',
      'user_lastname',
      'user_phone',
      'user_tags',
    ],
    exportData: players,
    exportFileName: `${t('Players')}.csv`,
  };

  const [loading, setLoading] = useState<boolean>(true);
  const searchPlayersToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchPlayers(input).finally(() => setLoading(false));
  };

  // Bulk selection
  const ability = useContext(AbilityContext);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS);
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<PlayerOutput>('user', players, queryableHelpers.paginationHelpers.getTotalElements());

  const bulkDelete = () => {
    bulkDeletePlayers({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      user_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      user_ids_to_ignore: Object.keys(deSelectedElements),
    }).then((result) => {
      const deletedIds: string[] = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setPlayers(players.filter(p => !deletedIds.includes(p.user_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Persons'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={searchPlayersToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setPlayers}
        entityPrefix="user"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
              <CreatePlayer
                onCreate={result => setPlayers([result, ...players])}
              />
            </Can>
          </Box>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          sx={{
            paddingTop: 0,
            ...(numberOfSelectedElements > 0 ? { backgroundColor: 'background.accent' } : {}),
          }}
          {...(numberOfSelectedElements === 0 ? { secondaryAction: <>&nbsp;</> } : {})}
        >
          {canManage && (
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={selectAll}
                disableRipple
                onChange={handleToggleSelectAll}
              />
            </ListItemIcon>
          )}
          {numberOfSelectedElements > 0 ? (
            <ListItemText
              primary={(
                <ToolBar
                  numberOfSelectedElements={numberOfSelectedElements}
                  handleClearSelectedElements={handleClearSelectedElements}
                  handleBulkDelete={bulkDelete}
                  canManage={canManage}
                  deleteConfirmationSingular={t('Do you want to delete this player?')}
                  deleteConfirmationPlural={t('Do you want to delete these {count} persons?', { count: String(numberOfSelectedElements) })}
                />
              )}
            />
          ) : (
            <>
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
            </>
          )}
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : players.map((player: PlayerOutput) => (
              <ListItem
                key={player.user_id}
                divider
                disablePadding
                secondaryAction={(
                  <PlayerPopover
                    user={player}
                    onDelete={result => setPlayers(players.filter(p => (p.user_id !== result)))}
                  />
                )}
              >
                <ListItemButton classes={{ root: classes.item }} onClick={() => navigate(`${PERSON_BASE_URL}/${player.user_id}`)}>
                  {canManage && (
                    <ListItemIcon
                      style={{ minWidth: 40 }}
                      onClick={event => onToggleEntity(player, event)}
                    >
                      <Checkbox
                        edge="start"
                        checked={
                          (selectAll && !(player.user_id in (deSelectedElements || {})))
                          || player.user_id in (selectedElements || {})
                        }
                        disableRipple
                      />
                    </ListItemIcon>
                  )}
                  <ListItemIcon>
                    <PersonOutlined color="primary" />
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
                            {header.value?.(player)}
                          </div>
                        ))}
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))}
      </List>
    </>
  );
};

export default Players;
