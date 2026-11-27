import { useEffect, useRef } from 'react';

import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../utils/api-types';

interface Options {
  version: PayloadVersionOutput;
  count: number;
  usages: PayloadUsagesOutput | null;
  active: boolean;
  onFocus: () => void;
  onLoaded: (usages: PayloadUsagesOutput) => void;
}

/** Opened to move injects: an older version in use is focused once, scrolled to the top, and given its usages once loaded. Returns the ref of its row. */
const useFocusMove = ({ version, count, usages, active, onFocus, onLoaded }: Options) => {
  const focused = useRef(false);
  const given = useRef(false);
  const row = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (active && !version.payload_is_latest && count > 0 && !focused.current) {
      focused.current = true;
      onFocus();
      row.current?.scrollIntoView({
        block: 'start',
        behavior: 'smooth',
      });
    }
  }, [active, version.payload_is_latest, count]);
  useEffect(() => {
    if (focused.current && usages && !given.current) {
      given.current = true;
      onLoaded(usages);
    }
  }, [count, usages]);
  return row;
};

export default useFocusMove;
