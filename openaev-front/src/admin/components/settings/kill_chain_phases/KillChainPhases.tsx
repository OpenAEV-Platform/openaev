import { RouteOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';

import { searchKillChainPhases } from '../../../../actions/KillChainPhase';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import ExportButton from '../../../../components/common/ExportButton';
import PaginatedList from '../../../../components/common/list/PaginatedList';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type KillChainPhase, type SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import TaxonomiesMenu from '../TaxonomiesMenu';
import CreateKillChainPhase from './CreateKillChainPhase';
import KillChainPhasePopover from './KillChainPhasePopover';

const inlineStyles: Record<string, CSSProperties> = {
  phase_kill_chain_name: { width: '20%' },
  phase_name: { width: '35%' },
  phase_order: { width: '15%' },
  phase_created_at: { width: '15%' },
};

const KillChainPhases = () => {
  // Standard hooks
  const { t, nsdt } = useFormatter();

  const [killChainPhases, setKillChainPhases] = useState<KillChainPhase[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'kill-chain-phases',
    buildSearchPagination({ sorts: initSorting('phase_order') }),
  );

  const searchKillChainPhasesToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchKillChainPhases(input).finally(() => setLoading(false));
  };

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'phase_kill_chain_name',
      label: 'Kill chain',
      isSortable: true,
      value: (killChainPhase: KillChainPhase) => (
        <Chip
          variant="outlined"
          color="primary"
          label={killChainPhase.phase_kill_chain_name}
          sx={{
            fontSize: 12,
            height: 20,
            float: 'left',
            textTransform: 'uppercase',
            borderRadius: 1,
            width: 120,
          }}
        />
      ),
    },
    {
      field: 'phase_name',
      label: 'Name',
      isSortable: true,
      value: (killChainPhase: KillChainPhase) => killChainPhase.phase_name,
    },
    {
      field: 'phase_order',
      label: 'Order',
      isSortable: true,
      value: (killChainPhase: KillChainPhase) => killChainPhase.phase_order?.toString(),
    },
    {
      field: 'phase_created_at',
      label: 'Created',
      isSortable: true,
      value: (killChainPhase: KillChainPhase) => nsdt(killChainPhase.phase_created_at),
    },
  ], []);

  // Export
  const exportProps = {
    exportType: 'kill_chain_phase',
    exportKeys: [
      'phase_kill_chain_name',
      'phase_name',
      'phase_order',
      'phase_created_at',
    ],
    exportData: killChainPhases,
    exportFileName: `${t('KillChainPhases')}.csv`,
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Taxonomies') }, {
            label: t('Kill chain phases'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={searchKillChainPhasesToLoad}
          searchPaginationInput={searchPaginationInput}
          setContent={setKillChainPhases}
          queryableHelpers={queryableHelpers}
          disableFilters
          topBarButtons={(
            <Box display="flex" gap={1} alignItems="center">
              <ExportButton
                totalElements={queryableHelpers.paginationHelpers.getTotalElements()}
                exportProps={exportProps}
              />
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
                <CreateKillChainPhase
                  onCreate={(result: KillChainPhase) => setKillChainPhases(prev => [result, ...prev])}
                />
              </Can>
            </Box>
          )}
        />
        <List>
          <ListItem
            divider={false}
            secondaryAction={<>&nbsp;</>}
            style={{ paddingTop: 0 }}
          >
            <ListItemIcon />
            <ListItemText
              style={{ textTransform: 'uppercase' }}
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
            ? <PaginatedListLoader Icon={RouteOutlined} headers={headers} headerStyles={inlineStyles} />
            : (
                <PaginatedList<KillChainPhase>
                  Icon={RouteOutlined}
                  headers={headers}
                  items={killChainPhases}
                  rowKey="phase_id"
                  itemWidth={inlineStyles}
                  secondaryAction={killChainPhase => (
                    <KillChainPhasePopover
                      killChainPhase={killChainPhase}
                      onUpdate={(result: KillChainPhase) => setKillChainPhases(prev => prev.map(k => (k.phase_id !== result.phase_id ? k : result)))}
                      onDelete={(result: string) => setKillChainPhases(prev => prev.filter(k => (k.phase_id !== result)))}
                    />
                  )}
                />
              )}
        </List>
      </div>
      <TaxonomiesMenu />
    </div>
  );
};

export default KillChainPhases;
