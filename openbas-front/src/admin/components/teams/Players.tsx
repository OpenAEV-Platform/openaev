import { HelpOutlineOutlined, PersonOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type OrganizationHelper, type UserHelper } from '../../../actions/helper';
import { fetchOrganizations } from '../../../actions/Organization';
import { searchPlayers } from '../../../actions/players/player-actions';
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
import { useHelper } from '../../../store';
import { type PlayerOutput, type SearchPaginationInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { Can } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
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
  const [searchId] = searchParams.getAll('id');

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

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Teams') }, {
          label: t('Players'),
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
        topBarButtons={
          <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
        }
      />
      <List>
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
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : players.map((player: PlayerOutput) => (
              <ListItem
                key={player.user_id}
                classes={{ root: classes.item }}
                divider
                secondaryAction={(
                  <PlayerPopover
                    user={player}
                    openEditOnInit={player.user_id === searchId}
                    onUpdate={result => setPlayers(players.map(p => (p.user_id !== result.user_id ? p : result)))}
                    onDelete={result => setPlayers(players.filter(p => (p.user_id !== result)))}
                  />
                )}
              >
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
              </ListItem>
            ))}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
        <CreatePlayer
          onCreate={result => setPlayers([result, ...players])}
        />
      </Can>
    </>
  );
};

export default Players;
