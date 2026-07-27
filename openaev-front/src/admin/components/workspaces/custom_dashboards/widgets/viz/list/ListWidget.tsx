import { KeyboardArrowRight } from '@mui/icons-material';
import {
  Box,
  List as MuiList,
  ListItem as MuiListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText, TablePagination,
} from '@mui/material';
import { useVirtualizer } from '@tanstack/react-virtual';
import { type ChangeEvent, memo, useMemo, useRef } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../../../../../../actions/attack_patterns/attackpattern-helper';
import { ROWS_PER_PAGE_OPTIONS } from '../../../../../../../components/common/queryable/pagination/usePaginationState';
import useBodyItemsStyles from '../../../../../../../components/common/queryable/style/style';
import Empty from '../../../../../../../components/Empty';
import { useFormatter } from '../../../../../../../components/i18n';
import Loader from '../../../../../../../components/Loader';
import { useHelper } from '../../../../../../../store';
import {
  type AttackPattern,
  type EsBase,
  type EsInjectExpectation,
  type ListConfiguration, type Pagination,
} from '../../../../../../../utils/api-types';
import { expectationTypeIcon } from '../../../../../common/ExpectationIconByType';
import AssetElementStyles from './elements/AssetElementStyles';
import buildStyles from './elements/ColumnStyles';
import DefaultElementStyles from './elements/DefaultElementStyles';
import getEntityLeadingIcon from './elements/EntityLeadingIcon';
import listConfigRenderer, { defaultRenderer } from './elements/ListColumnConfig';
import { getNavigationUrl } from './elements/ListNavigationHandler';

// Shared row height: used both for the list-item CSS and the virtualizer size
// estimate so the two stay aligned.
const ROW_HEIGHT = 50;

const useStyles = makeStyles()(theme => ({
  item: {
    'height': ROW_HEIGHT,
    'borderRadius': theme.shape.borderRadius,
    'transition': 'background 0.15s, box-shadow 0.15s',
    '&:hover': {
      backgroundColor: theme.palette.action.hover,
      boxShadow: `inset 2px 0 0 0 ${theme.palette.primary.main}`,
    },
  },
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
  itemClass: string;
}>(({ element, columns, columnStyles, bodyItemsStyles, attackPatterns, itemClass }) => {
  // Real router link (not a JS navigate) so ctrl/cmd+click opens a new tab.
  const url = getNavigationUrl(element);

  // Inject-expectation rows lead with the expectation-type icon (shield /
  // sensor / bug / support agent...); every other entity leads with the same
  // icon as its own list page (simulation play, scenario route, finding...).
  const expectationType = (element as EsInjectExpectation).inject_expectation_type;
  const LeadingIcon = expectationType ? expectationTypeIcon(expectationType) : getEntityLeadingIcon(element);

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

  const rowContent = (
    <>
      <ListItemIcon>
        <LeadingIcon color="primary" />
      </ListItemIcon>
      <ListItemText
        primary={(
          <div style={bodyItemsStyles.bodyItems}>
            {renderedColumns}
          </div>
        )}
      />
    </>
  );

  return (
    <MuiListItem
      component="div"
      divider
      disablePadding
      secondaryAction={url !== null ? <KeyboardArrowRight color="action" /> : <EmptySecondaryAction />}
    >
      {url !== null ? (
        <ListItemButton
          component={Link}
          to={url}
          classes={{ root: itemClass }}
          className="noDrag"
        >
          {rowContent}
        </ListItemButton>
      ) : (
        // Non-navigable row: disabled so it is neither focusable nor announced
        // as actionable; opacity restored so the content stays readable.
        <ListItemButton
          classes={{ root: itemClass }}
          className="noDrag"
          disabled
          sx={{
            '&.Mui-disabled': { opacity: 1 },
            'cursor': 'default',
          }}
        >
          {rowContent}
        </ListItemButton>
      )}
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
  // Render the pagination bar above the list (aligns with the app's list pages) instead of below
  // (default, used by embedded dashboard widget tiles).
  paginationAbove?: boolean;
  // Hide the built-in pagination entirely (the dashboard tile renders it in the widget title row).
  hidePagination?: boolean;
};

const ListWidget = ({
  widgetConfig,
  elements,
  currentPageNumber,
  elementsPerPage,
  totalElements,
  onPaginationChange,
  contentLoading = false,
  paginationAbove = false,
  hidePagination = false,
}: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();

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
      case 'asset':
        return buildStyles(columns, AssetElementStyles);
      default:
        return defaultStyles;
    }
  }, [columns, elements]);

  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: contentLoading ? 0 : elements.length,
    getScrollElement: () => scrollContainerRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 5,
  });

  if (!widgetConfig || columns.length === 0) {
    return <div>{t('No columns configured for this list.')}</div>;
  }

  const pagination = !hidePagination && elements.length > 0 && totalElements > elementsPerPage
    ? (
        <TablePagination
          component="div"
          rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
          count={totalElements}
          page={currentPageNumber}
          onPageChange={handleChangePage}
          rowsPerPage={elementsPerPage}
          onRowsPerPageChange={handleChangeRowsPerPage}
          sx={{
            'flexShrink': 0,
            [paginationAbove ? 'borderBottom' : 'borderTop']: theme => `1px solid ${theme.palette.divider}`,
            'minHeight': 0,
            '& .MuiTablePagination-toolbar': { minHeight: 40 },
          }}
        />
      )
    : null;

  return (
    <Box style={{
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
    }}
    >
      {paginationAbove && pagination}
      {contentLoading && <Loader variant="inElement" />}
      {!contentLoading && elements.length === 0 && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flex: 1,
        }}
        >
          <Empty message={t('No data to display')} />
        </div>
      )}
      {!contentLoading && elements.length > 0 && (
        <div
          ref={scrollContainerRef}
          style={{
            flex: 1,
            overflow: 'auto',
          }}
        >
          <MuiList
            component="div"
            role="list"
            disablePadding
            style={{
              height: virtualizer.getTotalSize(),
              position: 'relative',
            }}
          >
            {virtualizer.getVirtualItems().map((virtualRow) => {
              const element = elements[virtualRow.index];
              return (
                <div
                  key={element.base_id}
                  role="listitem"
                  style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    width: '100%',
                    height: ROW_HEIGHT,
                    transform: `translateY(${virtualRow.start}px)`,
                  }}
                >
                  <ListWidgetItem
                    element={element}
                    columns={columns}
                    columnStyles={columnStyles}
                    bodyItemsStyles={bodyItemsStyles}
                    attackPatterns={attackPatterns}
                    itemClass={classes.item}
                  />
                </div>
              );
            })}
          </MuiList>
        </div>
      )}

      {!paginationAbove && pagination}
    </Box>
  );
};

export default memo(ListWidget);
