import { DevicesOtherOutlined, KeyboardArrowRight } from '@mui/icons-material';
import {
  Box,
  List as MuiList,
  ListItem as MuiListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText, TablePagination,
} from '@mui/material';
import { type ChangeEvent, memo, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../../../../../../actions/attack_patterns/attackpattern-helper';
import { ROWS_PER_PAGE_OPTIONS } from '../../../../../../../components/common/queryable/pagination/usePaginationState';
import useBodyItemsStyles from '../../../../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../../../../components/i18n';
import Loader from '../../../../../../../components/Loader';
import { useHelper } from '../../../../../../../store';
import {
  type AttackPattern,
  type EsBase,
  type ListConfiguration, type Pagination,
} from '../../../../../../../utils/api-types';
import buildStyles from './elements/ColumnStyles';
import DefaultElementStyles from './elements/DefaultElementStyles';
import EndpointElementStyles from './elements/EndpointElementStyles';
import listConfigRenderer, { defaultRenderer } from './elements/ListColumnConfig';
import navigationHandlers from './elements/ListNavigationHandler';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

// Empty secondary action component to avoid recreation
const EmptySecondaryAction = memo(() => <>&nbsp;</>);
EmptySecondaryAction.displayName = 'EmptySecondaryAction';

// Memoized list item component
const ListWidgetItem = memo<{
  element: EsBase;
  columns: string[];
  columnStyles: Record<string, React.CSSProperties>;
  bodyItemsStyles: {
    bodyItems: React.CSSProperties;
    bodyItem: React.CSSProperties;
  };
  attackPatterns: AttackPattern[];
  onItemClick: (element: EsBase) => void;
  itemClass: string;
}>(({ element, columns, columnStyles, bodyItemsStyles, attackPatterns, onItemClick, itemClass }) => {
  const hasHandler = navigationHandlers[element.base_entity];

  const handleClick = useCallback(() => {
    onItemClick(element);
  }, [element, onItemClick]);

  const renderedColumns = useMemo(() => columns.map((col) => {
    const renderer = listConfigRenderer[col] ?? defaultRenderer;
    const value = element[col as keyof typeof element] as string | boolean | string[] | boolean[];
    return (
      <div
        key={col}
        style={{
          ...bodyItemsStyles.bodyItem,
          ...columnStyles[col],
        }}
      >
        {renderer(value, {
          element,
          attackPatterns,
        })}
      </div>
    );
  }), [columns, columnStyles, bodyItemsStyles, element, attackPatterns]);

  return (
    <MuiListItem
      divider
      disablePadding
      secondaryAction={hasHandler !== undefined ? <KeyboardArrowRight color="action" /> : <EmptySecondaryAction />}
    >
      <ListItemButton
        onClick={handleClick}
        classes={{ root: itemClass }}
        className="noDrag"
      >
        <ListItemIcon>
          <DevicesOtherOutlined color="primary" />
        </ListItemIcon>
        <ListItemText
          primary={(
            <div style={bodyItemsStyles.bodyItems}>
              {renderedColumns}
            </div>
          )}
        />
      </ListItemButton>
    </MuiListItem>
  );
});
ListWidgetItem.displayName = 'ListWidgetItem';

type Props = {
  widgetConfig: ListConfiguration;
  elements: EsBase[];
  currentPageNumber: number;
  elementsPerPage: number;
  totalElements: number;
  onPaginationChange: (paginationInput: Pagination) => void;
  contentLoading?: boolean;
};

const ListWidget = ({
  widgetConfig,
  elements,
  currentPageNumber,
  elementsPerPage,
  totalElements,
  onPaginationChange,
  contentLoading = false,
}: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const navigate = useNavigate();

  const { attackPatterns } = useHelper((helper: AttackPatternHelper) => ({ attackPatterns: helper.getAttackPatterns() }));

  const handleChangePage = (_: unknown, newPage: number) => {
    onPaginationChange({
      page: newPage,
      size: elementsPerPage,
    });
  };

  const handleChangeRowsPerPage = (event: ChangeEvent<HTMLInputElement>) => {
    const newRowsPerPage = parseInt(event.target.value, 10);
    onPaginationChange({
      page: currentPageNumber,
      size: newRowsPerPage,
    });
  };

  // Memoize columns array
  const columns = useMemo(() => widgetConfig.columns ?? [], [widgetConfig.columns]);

  // Memoize column styles based on entity type
  const columnStyles = useMemo(() => {
    const defaultStyles = buildStyles(columns, DefaultElementStyles);
    if (elements === undefined || elements.length === 0) {
      return defaultStyles;
    }
    const entityType = elements[0].base_entity;
    switch (entityType) {
      case 'endpoint':
        return buildStyles(columns, EndpointElementStyles);
      default:
        return defaultStyles;
    }
  }, [columns, elements]);

  const onListItemClick = useCallback((element: EsBase): void => {
    const handler = navigationHandlers[element.base_entity];
    handler?.(element, navigate);
  }, [navigate]);

  if (!widgetConfig || columns.length === 0) {
    return <div>{t('No columns configured for this list.')}</div>;
  }

  return (
    <Box style={{
      height: '100%',
      overflow: 'auto',
    }}
    >
      {elements.length > 0
        && (
          <TablePagination
            component="div"
            rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
            count={totalElements}
            page={currentPageNumber}
            onPageChange={handleChangePage}
            rowsPerPage={elementsPerPage}
            onRowsPerPageChange={handleChangeRowsPerPage}
          />
        )}

      <MuiList>
        <MuiListItem
          classes={{ root: classes.itemHead }}
          style={{ paddingTop: 0 }}
          secondaryAction={<EmptySecondaryAction />}
        >
          <ListItemIcon />
        </MuiListItem>
        {contentLoading && <Loader variant="inElement" />}
        {!contentLoading && elements.length === 0 && <div style={{ textAlign: 'center' }}>{t('No data to display')}</div>}
        {!contentLoading && elements.map(element => (
          <ListWidgetItem
            key={element.base_id}
            element={element}
            columns={columns}
            columnStyles={columnStyles}
            bodyItemsStyles={bodyItemsStyles}
            attackPatterns={attackPatterns}
            onItemClick={onListItemClick}
            itemClass={classes.item}
          />
        ))}
      </MuiList>
    </Box>
  );
};

export default memo(ListWidget);
