import { List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { LockPattern } from 'mdi-material-ui';
import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchAttackPatterns } from '../../../../actions/AttackPattern';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponent from '../../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../../components/common/pagination/SortHeadersComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style.js';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { Can } from '../../../../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types.js';
import TaxonomiesMenu from '../TaxonomiesMenu';
import AttackPatternPopover from './AttackPatternPopover';
import CreateAttackPattern from './CreateAttackPattern';

const useStyles = makeStyles()(() => ({
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
}));

const inlineStyles = {
  kill_chain_phase: {
    width: '20%',
    cursor: 'default',
  },
  attack_pattern_external_id: { width: '15%' },
  attack_pattern_name: { width: '35%' },
  attack_pattern_created_at: { width: '12%' },
  attack_pattern_updated_at: { width: '12%' },
};

const AttackPatterns = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const { killChainPhasesMap } = useHelper(helper => ({ killChainPhasesMap: helper.getKillChainPhasesMap() }));

  // Headers
  const headers = [
    {
      field: 'kill_chain_phase',
      label: 'Kill chain phase',
      isSortable: false,
    },
    {
      field: 'attack_pattern_external_id',
      label: 'External ID',
      isSortable: true,
    },
    {
      field: 'attack_pattern_name',
      label: 'Name',
      isSortable: true,
    },
    {
      field: 'attack_pattern_created_at',
      label: 'Created',
      isSortable: true,
    },
    {
      field: 'attack_pattern_updated_at',
      label: 'Updated',
      isSortable: true,
    },
  ];

  const [attackPatterns, setAttackPatterns] = useState([]);
  const [searchPaginationInput, setSearchPaginationInput] = useState({ sorts: initSorting('attack_pattern_external_id') });

  // Export
  const exportProps = {
    exportType: 'attack_patterns',
    exportKeys: [
      'attack_pattern_external_id',
      'attack_pattern_name',
      'attack_pattern_created_at',
      'attack_pattern_updated_at',
    ],
    exportData: attackPatterns,
    exportFileName: `${t('AttackPatterns')}.csv`,
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Settings') }, { label: t('Taxonomies') }, {
            label: t('Attack patterns'),
            current: true,
          }]}
        />
        <PaginationComponent
          fetch={searchAttackPatterns}
          searchPaginationInput={searchPaginationInput}
          setContent={setAttackPatterns}
          exportProps={exportProps}
        />
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
                <SortHeadersComponent
                  headers={headers}
                  inlineStylesHeaders={inlineStyles}
                  searchPaginationInput={searchPaginationInput}
                  setSearchPaginationInput={setSearchPaginationInput}
                />
              )}
            />
            <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
          </ListItem>
          {attackPatterns.map(attackPattern => (
            <ListItem
              key={attackPattern.attack_pattern_id}
              classes={{ root: classes.item }}
              divider={true}
            >
              <ListItemIcon>
                <LockPattern color="primary" />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div style={bodyItemsStyles.bodyItems}>
                    <div style={{
                      ...bodyItemsStyles.bodyItem,
                      ...inlineStyles.kill_chain_phase,
                    }}
                    >
                      {
                        attackPattern.attack_pattern_kill_chain_phases.at(0)
                          ? `[${killChainPhasesMap[attackPattern.attack_pattern_kill_chain_phases.at(0)]?.phase_kill_chain_name}] ${killChainPhasesMap[attackPattern.attack_pattern_kill_chain_phases.at(0)]?.phase_name}`
                          : '-'
                      }
                    </div>
                    <div style={{
                      ...bodyItemsStyles.bodyItem,
                      ...inlineStyles.attack_pattern_external_id,
                    }}
                    >
                      {attackPattern.attack_pattern_external_id}
                    </div>
                    <div style={{
                      ...bodyItemsStyles.bodyItem,
                      ...inlineStyles.attack_pattern_name,
                    }}
                    >
                      {attackPattern.attack_pattern_name}
                    </div>
                    <div style={{
                      ...bodyItemsStyles.bodyItem,
                      ...inlineStyles.attack_pattern_created_at,
                    }}
                    >
                      {' '}
                      {nsdt(attackPattern.attack_pattern_created_at)}
                    </div>
                    <div style={{
                      ...bodyItemsStyles.bodyItem,
                      ...inlineStyles.attack_pattern_updated_at,
                    }}
                    >
                      {nsdt(attackPattern.attack_pattern_updated_at)}
                    </div>
                  </div>
                )}
              />
              <ListItemSecondaryAction>
                <AttackPatternPopover
                  killChainPhasesMap={killChainPhasesMap}
                  attackPattern={attackPattern}
                  onUpdate={result => setAttackPatterns(attackPatterns.map(a => (a.attack_pattern_id !== result.attack_pattern_id ? a : result)))}
                  onDelete={result => setAttackPatterns(attackPatterns.filter(a => (a.attack_pattern_id !== result)))}
                />
              </ListItemSecondaryAction>
            </ListItem>
          ))}
        </List>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <CreateAttackPattern
            onCreate={result => setAttackPatterns([result, ...attackPatterns])}
          />
        </Can>
      </div>
      <TaxonomiesMenu />
    </div>
  );
};

export default AttackPatterns;
