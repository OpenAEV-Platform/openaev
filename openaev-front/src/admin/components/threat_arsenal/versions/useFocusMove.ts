import { useEffect, useRef } from 'react';

import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../utils/api-types';
import { usageCount } from './usages/usageCounts';

interface Options {
  version: PayloadVersionOutput;
  usages: PayloadUsagesOutput | null;
  active: boolean;
  onFocus: (usages: PayloadUsagesOutput) => void;
}

/** Opened to move injects: an older version in use is focused once, scrolled to the top. Returns the ref of its row. */
const useFocusMove = ({ version, usages, active, onFocus }: Options) => {
  const focused = useRef(false);
  const row = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (active && !version.payload_is_latest && usages && usageCount(usages) > 0 && !focused.current) {
      focused.current = true;
      onFocus(usages);
      row.current?.scrollIntoView({
        block: 'start',
        behavior: 'smooth',
      });
    }
  }, [active, version.payload_is_latest, usages]);
  return row;
};

export default useFocusMove;
