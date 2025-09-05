import { List as MuiList, ListItem as MuiListItem, ListItemIcon, ListItemText } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { initSorting } from '../../../../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../../../../components/i18n';
import {
  type EsBase,
  type EsEndpoint,
  type EsInject, type EsScenario,
  type EsSimulation,
  type EsVulnerableEndpoint,
} from '../../../../../../../utils/api-types';
import { type ListConfiguration, type Widget } from '../../../../../../../utils/api-types-custom';
import buildStyles from './elements/ColumnStyles';
import DefaultElementStyles from './elements/default/DefaultElementStyles';
import DefaultListElement from './elements/default/DefaultListElement';
import EndpointElementSecondaryAction from './elements/endpoint/EndpointElementSecondaryAction';
import EndpointElementStyles from './elements/endpoint/EndpointElementStyles';
import EndpointListElement from './elements/endpoint/EndpointListElement';
import InjectListElement from './elements/inject/InjectListElement';
import VulnerableEndpointElementSecondaryAction
  from './elements/vulnerableendpoint/VulnerableEndpointElementSecondaryAction';
import VulnerableEndpointListElement from './elements/vulnerableendpoint/VulnerableEndpointListElement';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

type Props = {
  config: Widget['widget_config'];
  elements: EsBase[];
};

const ListWidget = (props: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  // FIXME: we will always use ListConfiguration in this component
  const config = (): ListConfiguration => {
    return props.config as ListConfiguration;
  };

  const headersFromColumns = (columns: string[]): Header[] => {
    return columns.map((col) => {
      return {
        field: col,
        label: col,
        isSortable: false,
      };
    });
  };

  const stylesFromEntityType = (elements: EsBase[]) => {
    const defaultStyles = buildStyles(config().columns, DefaultElementStyles);
    if (elements === undefined || elements.length === 0) {
      return defaultStyles;
    }
    const entityType = elements[0].base_entity;
    switch (entityType) {
      case 'endpoint':
        return buildStyles(config().columns, EndpointElementStyles);
      default:
        return defaultStyles;
    }
  };

  const { queryableHelpers } = useQueryableWithLocalStorage('asset', buildSearchPagination({ sorts: initSorting('asset_name') }));

  const getTypedUiElement = (element: EsBase, columns: string[]) => {
    switch (element.base_entity) {
      case 'endpoint': return (<EndpointListElement element={element as EsEndpoint} columns={columns} />);
      case 'vulnerable-endpoint': return (<VulnerableEndpointListElement element={element as EsVulnerableEndpoint} columns={columns} />);
      case 'inject':
      case 'simulation':
      case 'scenario':
        return (<InjectListElement columns={columns} element={element as EsInject | EsSimulation | EsScenario} />);
      default: return (<DefaultListElement columns={columns} element={element} />);
    }
  };

  const getTypedSecondaryAction = (element: EsBase) => {
    switch (element.base_entity) {
      case 'endpoint': return (<EndpointElementSecondaryAction element={element as EsEndpoint} />);
      case 'vulnerable-endpoint': return (<VulnerableEndpointElementSecondaryAction element={element as EsVulnerableEndpoint} />);
        // TODO #3524
      /* case 'inject':
      case 'simulation':
      case 'scenario':
        return (<InjectElementSecondaryAction element={element as EsInject | EsSimulation | EsScenario} />); */
      default: return (<>&nbsp;</>);
    }
  };

  const columns = (widget_config: Widget['widget_config']) => {
    if (widget_config.widget_configuration_type === 'list') {
      return widget_config.columns;
    }
    throw 'Bad configuration: must be configuration of type list';
  };

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
              headers={headersFromColumns(columns(props.config))}
              inlineStylesHeaders={stylesFromEntityType(props.elements)}
              sortHelpers={queryableHelpers.sortHelpers}
            />
          )}
        />
      </MuiListItem>
      {props.elements.length === 0 && <div style={{ textAlign: 'center' }}>{t('No data to display')}</div>}
      {props.elements.map(e =>
        <MuiListItem key={e.base_id} divider disablePadding secondaryAction={getTypedSecondaryAction(e)}>{getTypedUiElement(e, columns(props.config))}</MuiListItem>,
      )}
    </MuiList>
  );
};

export default ListWidget;
