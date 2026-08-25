import { LabelOutlined } from '@mui/icons-material';
import { Chip, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';

import { searchMarkingDefinitions } from '../../../../actions/markings/marking-definition-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginatedList from '../../../../components/common/list/PaginatedList';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type MarkingDefinitionOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import CreateMarkingDefinition from './CreateMarkingDefinition';
import MarkingDefinitionPopover from './MarkingDefinitionPopover';

const inlineStyles: Record<string, CSSProperties> = {
  marking_name: { width: '35%' },
  marking_type: { width: '25%' },
  marking_order: { width: '20%' },
  marking_color: { width: '20%' },
};

// Settings > Security > Marking definitions: the tenant's classification scales (TLP, PAP, or its
// own). A marking is what a clearance is granted in and what a record is tagged with; this screen
// only manages the vocabulary, not the grants or the attachments.
const MarkingDefinitions = () => {
  const { t } = useFormatter();

  const headers: Header[] = useMemo(() => [
    {
      field: 'marking_name',
      label: 'Name',
      isSortable: true,
      value: (marking: MarkingDefinitionOutput) => marking.marking_name,
    },
    {
      field: 'marking_type',
      label: 'Type',
      isSortable: true,
      value: (marking: MarkingDefinitionOutput) => marking.marking_type,
    },
    {
      field: 'marking_order',
      label: 'Order',
      isSortable: true,
      value: (marking: MarkingDefinitionOutput) => String(marking.marking_order),
    },
    {
      field: 'marking_color',
      label: 'Color',
      isSortable: false,
      value: (marking: MarkingDefinitionOutput) => (marking.marking_color
        ? (
            <Chip
              size="small"
              label={marking.marking_color}
              style={{
                backgroundColor: marking.marking_color,
                color: '#000000',
              }}
            />
          )
        : '-'),
    },
  ], []);

  const availableFilterNames = [
    'marking_name',
    'marking_type',
  ];

  const [markings, setMarkings] = useState<MarkingDefinitionOutput[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'security-marking-definitions',
    buildSearchPagination({ sorts: initSorting('marking_type') }),
  );

  const [loading, setLoading] = useState<boolean>(true);
  const searchMarkingDefinitionsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchMarkingDefinitions(input).finally(() => setLoading(false));
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Security') }, {
            label: t('Marking definitions'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={searchMarkingDefinitionsToLoad}
          searchPaginationInput={searchPaginationInput}
          setContent={setMarkings}
          entityPrefix="marking"
          availableFilterNames={availableFilterNames}
          queryableHelpers={queryableHelpers}
          topBarButtons={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
              <CreateMarkingDefinition
                onCreate={(result: MarkingDefinitionOutput) => setMarkings(prev => [result, ...prev])}
              />
            </Can>
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
            ? <PaginatedListLoader Icon={LabelOutlined} headers={headers} headerStyles={inlineStyles} />
            : (
                <PaginatedList<MarkingDefinitionOutput>
                  Icon={LabelOutlined}
                  secondaryAction={marking => (
                    <MarkingDefinitionPopover
                      marking={marking}
                      onUpdate={(result: MarkingDefinitionOutput) => setMarkings(prev => prev.map(m => (m.marking_id !== result.marking_id ? m : result)))}
                      onDelete={(result: string) => setMarkings(prev => prev.filter(m => m.marking_id !== result))}
                    />
                  )}
                  headers={headers}
                  items={markings}
                  rowKey="marking_id"
                  itemWidth={inlineStyles}
                />
              )}
        </List>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default MarkingDefinitions;
