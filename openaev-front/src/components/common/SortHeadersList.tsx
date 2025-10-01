import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
import * as R from 'ramda';
import { type CSSProperties, type FunctionComponent, type ReactElement, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../i18n';

const useStyles = makeStyles()(() => ({
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
}));

export interface Header {
  field: string;
  label: string;
  isSortable: boolean;
  /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
  value?: (...values: any[]) => ReactElement | string | undefined;
}

interface Props {
  headers: Header[];
  inlineStylesHeaders: Record<string, CSSProperties>;
  initialSortBy: string;
  /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
  datas: any[];
  /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
  setDatas: (datas: any[]) => void;
}

const SortHeadersList: FunctionComponent<Props> = ({
  headers,
  inlineStylesHeaders,
  initialSortBy,
  datas,
  setDatas,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();

  const [sortBy, setSortBy] = useState(initialSortBy);
  const [sortAsc, setSortAsc] = useState(true);

  const sort = R.sortWith(
    sortAsc
      ? [R.ascend(R.prop(sortBy))]
      : [R.descend(R.prop(sortBy))],
  );

  const reverseBy = (field: string) => {
    setSortBy(field);
    setSortAsc(!sortAsc);
    setDatas(sort(datas));
  };

  const sortComponent = (asc: boolean) => {
    return asc
      ? (
          <ArrowDropDownOutlined className={classes.iconSort} />
        )
      : (
          <ArrowDropUpOutlined className={classes.iconSort} />
        );
  };

  const sortHeader = (header: Header, style: CSSProperties) => {
    if (header.isSortable) {
      return (
        <div key={header.label} style={style} onClick={() => reverseBy(header.field)}>
          <span>{t(header.label)}</span>
          {sortBy === header.field ? sortComponent(sortAsc) : ''}
        </div>
      );
    }
    return (
      <div key={header.label} style={style}>
        <span>{t(header.label)}</span>
      </div>
    );
  };

  return (
    <>
      {headers.map((header: Header) => (sortHeader(header, inlineStylesHeaders[header.field])))}
    </>
  );
};

export default SortHeadersList;
