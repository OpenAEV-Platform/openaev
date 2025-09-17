import { DevicesOtherOutlined, KeyboardArrowRight } from '@mui/icons-material';
import {
  List as MuiList,
  ListItem as MuiListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Tooltip,
} from '@mui/material';
import { useNavigate } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { initSorting } from '../../../../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../../../components/i18n';
import { type EsBase } from '../../../../../../../utils/api-types';
import { type ListConfiguration } from '../../../../../../../utils/api-types-custom';
import buildStyles from './elements/ColumnStyles';
import DefaultElementStyles from './elements/DefaultElementStyles';
import EndpointElementStyles from './elements/EndpointElementStyles';
import listConfigRenderer from './elements/LIstColumnConfig';
import navigationHandlers from './elements/ListNavigationHandler';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

type Props = {
  widgetConfig: ListConfiguration;
  elements: EsBase[];
};

const ListWidget = ({ widgetConfig, elements }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const navigate = useNavigate();

  const headersFromColumns = (widgetConfig.columns ?? []).map(col => ({
    field: col,
    label: col,
    isSortable: false,
  }));

  const stylesFromEntityType = (elements: EsBase[]) => {
    const defaultStyles = buildStyles(widgetConfig.columns, DefaultElementStyles);
    if (elements === undefined || elements.length === 0) {
      return defaultStyles;
    }
    const entityType = elements[0].base_entity;
    switch (entityType) {
      case 'endpoint':
        return buildStyles(widgetConfig.columns, EndpointElementStyles);
      default:
        return defaultStyles;
    }
  };

  const { queryableHelpers } = useQueryableWithLocalStorage('list-widget', buildSearchPagination({ sorts: initSorting('') }));

  const getSecondaryActionByBaseEntity = (element: EsBase) => {
    return navigationHandlers[element.base_entity] ? <KeyboardArrowRight color="action" /> : <>&nbsp;</>;
  };

  const elementsFromColumn = (
    column: string,
    element: EsBase,
  ) => {
    const renderer = listConfigRenderer[column];
    const value = element[column as keyof typeof element];

    if (renderer) {
      return renderer(value, element);
    }

    const text = value?.toString() ?? '';
    return (
      <Tooltip title={text} placement="bottom-start">
        <span>{text}</span>
      </Tooltip>
    );
  };

  const onListItemClick = (element: EsBase): void => {
    const handler = navigationHandlers[element.base_entity];
    handler?.(element, navigate);
  };

  if (!widgetConfig || !widgetConfig.columns || widgetConfig.columns.length === 0) {
    return <div>{t('No columns configured for this list.')}</div>;
  }

  return (
    <MuiList>
      <MuiListItem
        classes={{ root: classes.itemHead }}
        style={{ paddingTop: 0 }}
        secondaryAction={(<>&nbsp;</>)}
      >
        <ListItemIcon />
        <ListItemText
          primary={(
            <SortHeadersComponentV2
              headers={headersFromColumns}
              inlineStylesHeaders={stylesFromEntityType(elements)}
              sortHelpers={queryableHelpers.sortHelpers}
            />
          )}
        />
      </MuiListItem>
      {elements.length === 0 && <div style={{ textAlign: 'center' }}>{t('No data to display')}</div>}
      {elements.map(e => (
        <MuiListItem key={e.base_id} divider disablePadding secondaryAction={getSecondaryActionByBaseEntity(e)}>
          <ListItemButton
            key={e.base_id}
            onClick={() => onListItemClick(e)}
            classes={{ root: classes.item }}
            className="noDrag"
          >
            <ListItemIcon>
              <DevicesOtherOutlined color="primary" />
            </ListItemIcon>
            <ListItemText
              primary={(
                <div style={bodyItemsStyles.bodyItems}>
                  {widgetConfig.columns.map(col => (
                    <div
                      key={col}
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...stylesFromEntityType(elements)[col],
                      }}
                    >
                      {elementsFromColumn(col, e)}
                    </div>
                  ))}
                </div>
              )}
            />
          </ListItemButton>
        </MuiListItem>
      ),
      )}
    </MuiList>
  );
};

export default ListWidget;
