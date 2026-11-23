import { useEffect, useState } from 'react';

/**
 * What `load` gives for `key`, loaded again each time the key changes; `empty` without key, while
 * loading or when the call fails. A call superseded by a newer key never overwrites its result.
 */
const useLoadedOnKey = <T>(key: string | undefined, load: (key: string) => Promise<T>, empty: T) => {
  const [value, setValue] = useState<T>(empty);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setValue(empty);
    setLoading(!!key);
    if (!key) return undefined;
    let cancelled = false;
    load(key)
      .then((loaded) => {
        if (!cancelled) setValue(loaded);
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [key]);

  return {
    value,
    loading,
  };
};

export default useLoadedOnKey;
