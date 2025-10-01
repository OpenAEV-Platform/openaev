import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../i18n';
import { type Header } from '../../SortHeadersList';
import useBodyItemsStyles from '../style/style';
import { type SortHelpers } from './SortHelpers';

const useStyles = makeStyles()(() => ({
  sortableHeaderItem: {
    display: 'flex',
    cursor: 'pointer',
    alignItems: 'center',
    fontWeight: '700',
  },
  headerItemText: {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    fontWeight: '700',
  },
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
        style={{
          ...bodyItemsStyles.bodyItem,
          ...style,
        }}
      >
        <Tooltip title={t(header.label)}>
          <div className={classes.headerItemText}>{t(header.label)}</div>
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
