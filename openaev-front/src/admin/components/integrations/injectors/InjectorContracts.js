import { SmartButtonOutlined } from '@mui/icons-material';
import { Chip, List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { useState } from 'react';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchInjectorContracts } from '../../../../actions/InjectorContracts';
import PaginationComponent from '../../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../../components/common/pagination/SortHeadersComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import { useFormatter } from '../../../../components/i18n';
import ItemDomains from '../../../../components/ItemDomains.tsx';
import { useHelper } from '../../../../store';
import CreateInjectorContract from './injector_contracts/CreateInjectorContract';
import InjectorContractPopover from './injector_contracts/InjectorContractPopover';

const useStyles = makeStyles()(() => ({
  container: { marginTop: 20 },
  list: { marginTop: 10 },
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    marginRight: 5,
  },
}));

const headerStyles = {
  injector_contract_labels: { width: '20%' },
  injector_contract_domains: { width: '20%' },
  kill_chains: { width: '13%' },
  attack_patterns: { width: '35%' },
  injector_contract_updated_at: { width: '12%' },
};

const inlineStyles = {
  injector_contract_labels: {
    float: 'left',
    width: '20%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  injector_contract_domains: {
    float: 'left',
    width: '20%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  kill_chains: {
    float: 'left',
    width: '13%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  attack_patterns: {
    float: 'left',
    width: '35%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  injector_contract_updated_at: {
    float: 'left',
    width: '12%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

const InjectorContracts = () => {
  // Standard hooks
  const { classes } = useStyles();
  const { injectorId } = useParams();
  const { t, tPick, nsdt } = useFormatter();
  const { injector, attackPatternsMap, killChainPhasesMap } = useHelper(helper => ({
    injector: helper.getInjector(injectorId),
    attackPatternsMap: helper.getAttackPatternsMap(),
    killChainPhasesMap: helper.getKillChainPhasesMap(),
  }));
  // Headers
  const headers = [
    {
      field: 'injector_contract_labels',
      label: 'Name',
      isSortable: true,
    },
    {
      field: 'kill_chains',
      label: 'Kill chains',
      isSortable: false,
    },
    {
      field: 'injector_contract_domains',
      label: t('Payload domains'),
      isSortable: true,
    },
    {
      field: 'attack_patterns',
      label: 'Attack patterns',
      isSortable: false,
    },
    {
      field: 'injector_contract_updated_at',
      label: 'Updated at',
      isSortable: true,
    },
  ];

  const [injectorContracts, setInjectorContracts] = useState([]);
  const [searchPaginationInput, setSearchPaginationInput] = useState({
    sorts: initSorting('injector_contract_labels'),
    filterGroup: {
      mode: 'and',
      filters: [
        {
          key: 'injector_contract_injector',
          operator: 'eq',
          values: [injectorId],
        }],
    },
  });

  // Export
  const exportProps = {
    exportType: 'attack_patterns',
    exportKeys: [
      'injector_contract_labels',
      'attack_patterns',
      'injector_contract_updated_at',
    ],
    exportData: injectorContracts,
    exportFileName: `${t('InjectorContracts')}.csv`,
  };

  return (
    <div className={classes.container}>
      <PaginationComponent
        fetch={searchInjectorContracts}
        searchPaginationInput={searchPaginationInput}
        setContent={setInjectorContracts}
        exportProps={exportProps}
      />
      <div className="clearfix" />
      <List classes={{ root: classes.list }}>
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
              <SortHeadersComponent
                headers={headers}
                inlineStylesHeaders={headerStyles}
                searchPaginationInput={searchPaginationInput}
                setSearchPaginationInput={setSearchPaginationInput}
              />
            )}
          />
          <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
        </ListItem>
        {injectorContracts.map(injectorContract => (
          <ListItem
            key={injectorContract.injector_contract_id}
            classes={{ root: classes.item }}
            divider={true}
          >
            <ListItemIcon>
              <SmartButtonOutlined color="primary" />
            </ListItemIcon>
            <ListItemText
              primary={(
                <div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.injector_contract_labels}
                  >
                    {tPick(injectorContract.injector_contract_labels)}
                  </div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.kill_chains}
                  >
                    {
                      Array.from(
                        new Set(
                          injectorContract.injector_contract_attack_patterns
                            .map(n => attackPatternsMap[n]?.attack_pattern_kill_chain_phases ?? [])
                            .flat()
                            .map(o => killChainPhasesMap[o]?.phase_kill_chain_name ?? ''),
                        ),
                      ).map((killChain) => {
                        return (
                          <Chip
                            key={killChain}
                            variant="outlined"
                            classes={{ root: classes.chipInList }}
                            style={{ width: 120 }}
                            color="primary"
                            label={killChain}
                          />
                        );
                      })
                    }
                  </div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.injector_contract_domains}
                  >
                    <ItemDomains domains={injectorContract.injector_contract_domains} variant="reduced-view" />
                  </div>

                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.attack_patterns}
                  >
                    {injectorContract.injector_contract_attack_patterns.map(n => `[${attackPatternsMap[n]?.attack_pattern_external_id ?? ''}] ${attackPatternsMap[n]?.attack_pattern_name ?? ''}`).join(', ')}
                  </div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.injector_contract_updated_at}
                  >
                    {nsdt(injectorContract.injector_contract_updated_at)}
                  </div>
                </div>
              )}
            />
            <ListItemSecondaryAction>
              <InjectorContractPopover
                injectorContract={injectorContract}
                killChainPhasesMap={killChainPhasesMap}
                attackPatternsMap={attackPatternsMap}
                onUpdate={result => setInjectorContracts(injectorContracts.map(ic => (ic.injector_contract_id !== result.injector_contract_id ? ic : result)))}
                isPayloadInjector={injector.injector_payloads}
              />
            </ListItemSecondaryAction>
          </ListItem>
        ))}
      </List>
      {injector.injector_custom_contracts && (
        <CreateInjectorContract
          injector={injector}
          injectorContracts={injectorContracts}
          killChainPhasesMap={killChainPhasesMap}
          attackPatternsMap={attackPatternsMap}
          onCreated={() => {
            setSearchPaginationInput({ ...searchPaginationInput });
          }}
        />
      )}
    </div>
  );
};

export default InjectorContracts;
