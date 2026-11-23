import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
import * as R from 'ramda';
import { useState } from 'react';

import { useFormatter } from '../components/i18n';

const useSearchAndFilter = (
  schema,
  defaultSortKey,
  searchColumns,
  options = {}, // {orderAsc, defaultKeyword }
) => {
  const { t } = useFormatter();
  const [order, setOrder] = useState({
    sortBy: `${schema ? `${schema}_` : ''}${defaultSortKey}`,
    orderAsc: options?.orderAsc ?? true,
  });
  const [keyword, setKeyword] = useState(options?.defaultKeyword ?? '');
  const [tags, setTags] = useState([]);
  const handleAddTag = (value) => {
    setTags(R.uniq(R.append(value, tags)));
  };
  const handleRemoveTag = (value) => {
    const remainingTags = R.filter(n => n.id !== value, tags);
    setTags(remainingTags);
  };
  const handleSearch = value => setKeyword(value);
  const reverseBy = (field) => {
    setOrder({
      sortBy: field,
      orderAsc: !order.orderAsc,
    });
  };
  const buildHeader = (field, label, isSortable, styles) => {
    const sortComponent = order.orderAsc
      ? (
          <ArrowDropDownOutlined style={styles.iconSort} />
        )
      : (
          <ArrowDropUpOutlined style={styles.iconSort} />
        );
    if (isSortable) {
      return (
        <div style={styles[field]} onClick={() => reverseBy(field)}>
          <span>{t(label)}</span>
          {order.sortBy === field ? sortComponent : ''}
        </div>
      );
    }
    return (
      <div style={styles[field]}>
        <span>{t(label)}</span>
      </div>
    );
  };
  const filterAndSort = (elements) => {
    const filterByKeyword = (e) => {
      const isEmptyKeyword = keyword === '';
      const isKnownColumn = searchColumns
        .map(d => e[`${schema ? `${schema}_` : ''}${d}`] || '')
        .map(data => (typeof data === 'object' ? JSON.stringify(data) : data))
        .map(info => info.toLowerCase().indexOf(keyword.toLowerCase()) !== -1)
        .reduce((a, b) => a || b);
      return isEmptyKeyword || isKnownColumn;
    };
    // A missing value sorts as '' (it was R.propOr('', sortBy)), unlike ascendBy where it compares equal to all
    const sortValue = e => e[order.sortBy] ?? '';
    const compareElements = (a, b) => {
      const [x, y] = order.orderAsc ? [sortValue(a), sortValue(b)] : [sortValue(b), sortValue(a)];
      if (x < y) return -1;
      if (x > y) return 1;
      return 0;
    };
    const sort = items => items.toSorted(compareElements);
    return defaultSortKey
      ? R.pipe(
          R.filter(
            n => tags.length === 0
              || R.any(
                filter => R.includes(filter, n[`${schema}_tags`] || []),
                R.pluck('id', tags),
              ),
          ),
          R.filter(filterByKeyword),
          sort,
        )(elements)
      : R.pipe(R.filter(filterByKeyword))(elements);
  };
  return {
    keyword,
    order,
    tags,
    handleAddTag,
    handleRemoveTag,
    handleSearch,
    buildHeader,
    filterAndSort,
  };
};

export default useSearchAndFilter;
