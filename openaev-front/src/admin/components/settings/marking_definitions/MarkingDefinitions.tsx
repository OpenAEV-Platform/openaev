import { LensOutlined, SecurityOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import {
  createMarkingDefinition,
  fetchMarkingDefinitions,
  searchMarkingDefinitions,
} from '../../../../actions/marking_definitions/marking-definition-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import {
  type MarkingDefinitionInput,
  type MarkingDefinitionOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import MarkingDefinitionForm from './MarkingDefinitionForm';
import MarkingDefinitionPopover from './MarkingDefinitionPopover';
import {
  extractMarkingDefinitionFromStoreResult,
  type MarkingDefinitionStoreResult,
} from './MarkingDefinitionStoreHelper';

const useStyles = makeStyles()(() => ({ itemHead: { textTransform: 'uppercase' } }));

const inlineStyles: Record<string, CSSProperties> = {
  marking_definition_type: { width: '18%' },
  marking_definition_definition: { width: '26%' },
  marking_definition_color: { width: '16%' },
  marking_definition_order: { width: '10%' },
  marking_definition_created_at: { width: '20%' },
};

const MarkingDefinitions = () => {
  const { t, fldt } = useFormatter();
  const dispatch = useAppDispatch();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const [markingDefinitions, setMarkingDefinitions] = useState<MarkingDefinitionOutput[]>([]);
  const [openCreate, setOpenCreate] = useState(false);

  useDataLoader(() => {
    dispatch(fetchMarkingDefinitions());
  });

  const availableFilterNames = [
    'marking_definition_type',
    'marking_definition_definition',
    'marking_definition_color',
    'marking_definition_order',
    'marking_definition_created_at',
  ];

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'marking_definitions',
    buildSearchPagination({}),
  );

  const upsert = (result: MarkingDefinitionOutput) =>
    setMarkingDefinitions((prev) => {
      return prev.some(d => d.marking_definition_id === result.marking_definition_id)
        ? prev.map(d =>
            d.marking_definition_id === result.marking_definition_id ? result : d)
        : [...prev, result];
    });

  const headers: Header[] = useMemo(
    () => [
      {
        field: 'marking_definition_type',
        label: 'Type',
        isSortable: true,
        value: (item: MarkingDefinitionOutput) => item.marking_definition_type,
      },
      {
        field: 'marking_definition_definition',
        label: 'Definition',
        isSortable: true,
        value: (item: MarkingDefinitionOutput) => item.marking_definition_definition,
      },
      {
        field: 'marking_definition_color',
        label: 'Color',
        isSortable: true,
        value: (item: MarkingDefinitionOutput) => (
          <Box sx={{
            alignItems: 'center',
            display: 'flex',
            gap: 1,
          }}
          >
            {item.marking_definition_color ? (
              <LensOutlined sx={{
                color: item.marking_definition_color,
                fontSize: 14,
              }}
              />
            ) : null}
            <span>{item.marking_definition_color ?? '-'}</span>
          </Box>
        ),
      },
      {
        field: 'marking_definition_order',
        label: 'Order',
        isSortable: true,
        value: (item: MarkingDefinitionOutput) => (item.marking_definition_order ?? '-').toString(),
      },
      {
        field: 'marking_definition_created_at',
        label: 'Creation date',
        isSortable: true,
        value: (item: MarkingDefinitionOutput) => fldt(item.marking_definition_created_at),
      },
    ],
    [fldt],
  );

  const submitCreate = (input: MarkingDefinitionInput) => {
    return dispatch(createMarkingDefinition(input))
      .then((result: MarkingDefinitionStoreResult) => {
        const createdMarkingDefinition = extractMarkingDefinitionFromStoreResult(result);
        if (createdMarkingDefinition) {
          upsert(createdMarkingDefinition);
          setOpenCreate(false);
        }
        return result;
      })
      .catch((error: unknown) => error);
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[
            { label: t(SETTINGS_LABEL) },
            { label: t('Security') },
            {
              label: t('Marking definitions'),
              current: true,
            },
          ]}
        />
        <PaginationComponentV2
          fetch={searchMarkingDefinitions}
          searchPaginationInput={searchPaginationInput}
          setContent={setMarkingDefinitions}
          availableFilterNames={availableFilterNames}
          queryableHelpers={queryableHelpers}
          entityPrefix="marking_definition"
          topBarButtons={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.MARKING_DEFINITION}>
              <ButtonCreate onClick={() => setOpenCreate(true)} label={t('Add a marking definition')} />
            </Can>
          )}
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
          {markingDefinitions.map((item: MarkingDefinitionOutput) => (
            <ListItem
              key={item.marking_definition_id}
              secondaryAction={(
                <MarkingDefinitionPopover
                  markingDefinition={item}
                  onDelete={result =>
                    setMarkingDefinitions(prev =>
                      prev.filter(d => d.marking_definition_id !== result))}
                  onUpdate={upsert}
                />
              )}
              divider
            >
              <ListItemIcon>
                <SecurityOutlined color="primary" />
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
                        {header.value?.(item)}
                      </div>
                    ))}
                  </div>
                )}
              />
            </ListItem>
          ))}
        </List>
      </div>
      <SecurityMenu />
      <Drawer
        open={openCreate}
        handleClose={() => setOpenCreate(false)}
        title={t('Add a marking definition')}
      >
        <MarkingDefinitionForm onSubmit={submitCreate} />
      </Drawer>
    </div>
  );
};

export default MarkingDefinitions;
