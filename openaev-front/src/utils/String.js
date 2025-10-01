import { isNotEmptyField } from './utils';

export const truncate = (str, limit) => {
  if (str === undefined || str === null || str.length <= limit) {
    return str;
  }
  const trimmedStr = str.substr(0, limit);
  if (!trimmedStr.includes(' ')) {
    return `${trimmedStr}...`;
  }
  return `${trimmedStr.substr(
    0,
    Math.min(trimmedStr.length, trimmedStr.lastIndexOf(' ')),
  )}...`;
};

export const resolveUserName = (user) => {
  if (user.user_firstname && user.user_lastname) {
    return `${user.user_firstname} ${user.user_lastname}`;
  }
  return user.user_email;
};

export const resolveUserNames = (users, withEmailAddress = false) => {
  return users
    .map((user) => {
      if (user.user_firstname && user.user_lastname) {
        return `${user.user_firstname} ${user.user_lastname}${
          withEmailAddress ? ` (${user.user_email})` : ''
        }`;
      }
      return user.user_email;
    })
    .join(', ');
};

export const emptyFilled = str => (isNotEmptyField(str) ? str : '-');

// Extract the first two items as visible chips
export const getVisibleItems = (items, limit) => {
  return items?.slice(0, limit);
};

// Generate label with name of remaining items
export const getLabelOfRemainingItems = (items, start, property) => {
  return items?.slice(start, items?.length).map(
    item => item[property],
  ).join(', ');
};

// Calculate the number of remaining items
export const getRemainingItemsCount = (items, visibleItems) => {
  return (items && visibleItems && items.length - visibleItems.length) || null;
};

// Compute label for status
export const computeLabel = (status) => {
  if (status === 'PENDING') {
    return 'Pending validation';
  }
  if (status === 'SUCCESS') {
    return 'Success';
  }
  if (status === 'PARTIAL') {
    return 'Partial';
  }
  return 'Failed';
};

export const capitalize = (text) => {
  return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase();
};

export const formatMacAddress = (mac) => {
  const address = mac.toUpperCase();
  return address.match(/.{1,2}/g)?.join(':') || '-';
};

export const formatIp = (ip) => {
  return ip.toUpperCase();
};
