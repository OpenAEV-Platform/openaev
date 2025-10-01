import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
import * as R from 'ramda';
import { useState } from 'react';

import { useFormatter } from '../components/i18n';

const useSearchAnFilter = (
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
    const sort = R.sortWith(
      order.orderAsc
        ? [R.ascend(R.propOr('', order.sortBy))]
        : [R.descend(R.propOr('', order.sortBy))],
    );
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

export default useSearchAnFilter;
