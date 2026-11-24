import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
import * as R from 'ramda';
import { useState } from 'react';

import { useFormatter } from '../components/i18n';
import { compare } from './Sorting';

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
    // deep equality, as Ramda's uniq did: tag options are rebuilt objects
    setTags([...tags, value].filter((tag, index, all) => all.findIndex(other => R.equals(other, tag)) === index));
  };
  const handleRemoveTag = (value) => {
    const remainingTags = tags.filter(n => n.id !== value);
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
    // A missing value sorts as '', unlike ascendBy where it compares equal to every value
    const sortValue = e => e[order.sortBy] ?? '';
    const compareElements = (a, b) => (order.orderAsc
      ? compare(sortValue(a), sortValue(b))
      : compare(sortValue(b), sortValue(a)));
    const sort = items => items.toSorted(compareElements);
    return defaultSortKey
      ? R.pipe(
          list => list.filter(
            n => tags.length === 0
              || tags.map(tag => tag.id).some(
                filter => R.includes(filter, n[`${schema}_tags`] || []),
              ),
          ),
          list => list.filter(n => filterByKeyword(n)),
          sort,
        )(elements)
      : R.pipe(list => list.filter(n => filterByKeyword(n)))(elements);
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
