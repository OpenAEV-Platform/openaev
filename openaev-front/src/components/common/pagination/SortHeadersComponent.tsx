import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
import { type CSSProperties, type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type SearchPaginationInput } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import useBodyItemsStyles from '../queryable/style/style';

const useStyles = makeStyles()(() => ({
  sortableHeaderItem: {
    display: 'flex',
    height: 20,
    fontSize: 12,
    fontWeight: '700',
    cursor: 'pointer',
    paddingRight: 10,
    alignItems: 'center',
  },
  headerItemText: {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    fontWeight: '700',
  },
}));

interface Header {
  field: string;
  label: string;
  isSortable: boolean;
}

interface Props {
  headers: Header[];
  inlineStylesHeaders: Record<string, CSSProperties>;
  searchPaginationInput: SearchPaginationInput;
  setSearchPaginationInput: (datas: SearchPaginationInput) => void;
  defaultSortAsc?: boolean;
}

/**
 * @deprecated Need to migrate to the new pagination system: SortHeadersComponentV2
 */
const SortHeadersComponent: FunctionComponent<Props> = ({
  headers,
  inlineStylesHeaders,
  searchPaginationInput,
  setSearchPaginationInput,
  defaultSortAsc = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const [sortBy, setSortBy] = useState(searchPaginationInput.sorts?.[0].property ?? '');
  const [sortAsc, setSortAsc] = useState(defaultSortAsc);

  const reverseBy = (field: string) => {
    setSortBy(field);
    setSortAsc(!sortAsc);

    const sorts = [{
      property: field,
      direction: (sortAsc ? 'ASC' : 'DESC'),
    }];

    setSearchPaginationInput({
      ...searchPaginationInput,
      sorts,
    });
  };

  const sortComponent = (asc: boolean) => {
    return asc ? (<ArrowDropDownOutlined />) : (<ArrowDropUpOutlined />);
  };

  const sortHeader = (header: Header, style: CSSProperties) => {
    if (header.isSortable) {
      return (
        <div key={header.field} className={classes.sortableHeaderItem} style={style} onClick={() => reverseBy(header.field)}>
          <div className={classes.headerItemText}>{t(header.label)}</div>
          {sortBy === header.field ? sortComponent(sortAsc) : ''}
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
        <div className={classes.headerItemText}>{t(header.label)}</div>
      </div>
    );
  };

  return (
    <div style={bodyItemsStyles.bodyItems}>
      {headers.map((header: Header) => (sortHeader(header, inlineStylesHeaders[header.field])))}
    </div>
  );
};

export default SortHeadersComponent;
