import purify from 'dompurify';

import { isNotEmptyField } from './utils';

interface UserWithName {
  user_firstname?: string;
  user_lastname?: string;
  user_email?: string;
}

export const truncate = (str: string | null | undefined, limit: number): string | null | undefined => {
  if (str === undefined || str === null || str.length <= limit) {
    return str;
  }
  const trimmedStr = str.slice(0, limit);
  if (!trimmedStr.includes(' ')) {
    return `${trimmedStr}...`;
  }
  return `${trimmedStr.slice(
    0,
    Math.min(trimmedStr.length, trimmedStr.lastIndexOf(' ')),
  )}...`;
};

export const resolveUserName = (user: UserWithName): string => {
  if (user.user_firstname && user.user_lastname) {
    return `${user.user_firstname} ${user.user_lastname}`;
  }
  return user.user_email ?? '';
};

export const resolveUserNames = (users: UserWithName[], withEmailAddress = false): string => {
  return users
    .map((user) => {
      if (user.user_firstname && user.user_lastname) {
        return `${user.user_firstname} ${user.user_lastname}${
          withEmailAddress && user.user_email ? ` (${user.user_email})` : ''
        }`;
      }
      return user.user_email ?? '';
    })
    .join(', ');
};

export const emptyFilled = (str: string | null | undefined): string => (isNotEmptyField(str) ? str : '-');

// Extract the first items as visible chips
export const getVisibleItems = <T>(items: T[] | null | undefined, limit: number): T[] | undefined => {
  return items?.slice(0, limit);
};

// Generate label with name of remaining items
export const getLabelOfRemainingItems = <T extends object>(
  items: T[] | null | undefined,
  start: number,
  property: keyof T,
): string | undefined => {
  return items?.slice(start, items?.length).map(
    item => String(item[property]),
  ).join(', ');
};

// Calculate the number of remaining items
export const getRemainingItemsCount = <T>(
  items: T[] | null | undefined,
  visibleItems: T[] | null | undefined,
): number | null => {
  return (items && visibleItems && items.length - visibleItems.length) || null;
};

export type ExpectationStatus = 'PENDING' | 'SUCCESS' | 'PARTIAL' | 'FAILED';

// Compute label for status
export const computeLabel = (status: ExpectationStatus | string | undefined): string => {
  if (status === 'PENDING' || status === undefined) {
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

export const capitalize = (text: string): string => {
  if (!text) return '';
  return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase();
};

export const formatPrimitiveTypeLabel = (value: string): string => value
  .replace(/[_-]/g, ' ')
  .replace(/^./, c => c.toUpperCase());

export const formatMacAddress = (mac: string): string => {
  const address = mac.toUpperCase();
  return address.match(/.{1,2}/g)?.join(':') || '-';
};

export const formatIp = (ip: string): string => {
  // IPv4 addresses are numeric, IPv6 hex digits are case-insensitive
  // Return as-is since IP addresses don't need case transformation
  return ip;
};

const HTML_ESCAPE_MAP: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  '\'': '&#39;',
};

/**
 * Sanitizes a value about to be interpolated into an HTML string (e.g. a custom
 * ApexCharts tooltip rendered via innerHTML), so user-controlled data (labels,
 * names...) cannot break out of its text-node/attribute context and inject markup
 * or event handlers.
 *
 * @param {unknown} value Value to sanitize (coerced to a string; `null`/`undefined`
 * become an empty string).
 * @returns {string} The escaped, sanitized string, safe to interpolate as HTML text.
 */
export const sanitizeHtml = (value: unknown): string => {
  const escaped = String(value ?? '').replace(/[&<>"']/g, char => HTML_ESCAPE_MAP[char]);
  return purify.sanitize(escaped);
};
