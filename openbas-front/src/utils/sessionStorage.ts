/**
 * Set data in Session storage.
 * Data is stringified automatically.
 *
 * @param key Key in Session storage.
 * @param item Data to save.
 */
export function setSessionStorageItem<T>(key: string, item: T) {
  sessionStorage.setItem(key, JSON.stringify(item));
}

/**
 * Get data from Session storage.
 * Data is parsed automatically.
 *
 * @param key Key in Session storage.
 * @returns Data from Session storage.
 */
export function getSessionStorageItem<T>(key: string): T | null {
  const data = sessionStorage.getItem(key);
  return data ? JSON.parse(data) : null;
}
