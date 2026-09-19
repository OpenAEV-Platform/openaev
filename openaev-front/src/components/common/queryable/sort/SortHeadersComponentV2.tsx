import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../i18n';
import { type Header } from '../../SortHeadersList';
import useBodyItemsStyles from '../style/style';
import { type SortHelpers } from './SortHelpers';

// A column heading names its column, it does not compete with the values under
// it: secondary ink, like every other heading of a surface.
const useStyles = makeStyles()(theme => ({
  sortableHeaderItem: {
    display: 'flex',
    cursor: 'pointer',
    alignItems: 'center',
    fontWeight: '700',
    color: theme.palette.text.secondary,
  },
  headerItemText: {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    fontWeight: '700',
  },
  headerItem: { color: theme.palette.text.secondary },
}));

interface Props {
  headers: Header[];
  inlineStylesHeaders: Record<string, CSSProperties>;
  sortHelpers: SortHelpers;
}

const SortHeadersComponentV2: FunctionComponent<Props> = ({
  headers,
  inlineStylesHeaders,
  sortHelpers,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const sortComponent = (asc: boolean) => {
    return asc ? (<ArrowDropUpOutlined />) : (<ArrowDropDownOutlined />);
  };

  const sortHeader = (header: Header, style: CSSProperties) => {
    if (header.isSortable) {
      return (
        <div
          key={header.field}
          className={classes.sortableHeaderItem}
          style={{
            ...bodyItemsStyles.bodyItem,
            ...style,
          }}
          onClick={() => sortHelpers.handleSort(header.field)}
        >
          <div className={classes.headerItemText}>{t(header.label)}</div>
          {sortHelpers.getSortBy() === header.field ? sortComponent(sortHelpers.getSortAsc()) : ''}
        </div>
      );
    }
    return (
      <div
        key={header.field}
        className={classes.headerItem}
        style={{
          ...bodyItemsStyles.bodyItem,
          ...style,
        }}
      >
        <Tooltip>
          <TooltipTrigger asChild>
            <div className={classes.headerItemText}>{t(header.label)}</div>
          </TooltipTrigger>
          <TooltipContent>{t(header.label)}</TooltipContent>
        </Tooltip>
      </div>
    );
  };

  return (
    <div style={bodyItemsStyles.bodyItems}>
      {headers.map((header: Header) => (sortHeader(header, inlineStylesHeaders[header.field])))}
    </div>
  );
};

export default SortHeadersComponentV2;
