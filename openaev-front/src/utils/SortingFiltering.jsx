import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
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
    setTags(tags.some(tag => tag.id === value.id) ? tags : [...tags, value]);
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
    if (!defaultSortKey) {
      return elements.filter(n => filterByKeyword(n));
    }
    return sort(
      elements
        .filter(n => tags.length === 0 || tags.some(tag => (n[`${schema}_tags`] || []).includes(tag.id)))
        .filter(n => filterByKeyword(n)),
    );
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
