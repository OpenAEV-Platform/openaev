import { type CSSProperties } from 'react';

const minComputedWidthPercent = 10;

const buildStyles = (all_columns: string[] = [], staticStyles: Record<string, CSSProperties>) => {
  let fixedWidthsSum = 0;
  let columnsFixedWidthCount = 0;
  for (const col in all_columns) {
    if (col in staticStyles && staticStyles[col].width !== undefined) {
      const widthStr = staticStyles[col].width.toString();
      fixedWidthsSum += Number(widthStr.substring(1, widthStr.indexOf('%')));
      columnsFixedWidthCount++;
    }
  }

  let defaultStyle: CSSProperties;
  if (fixedWidthsSum >= 100) {
    defaultStyle = { width: `${minComputedWidthPercent}%` };
  } else {
    // this should never be 0-division because we only end up here when there is no explicit style, hence no width
    const divided = (100 - fixedWidthsSum) / (all_columns.length - columnsFixedWidthCount);
    defaultStyle = divided < minComputedWidthPercent ? { width: `${minComputedWidthPercent}%` } : { width: `${divided}%` };
  }

  return new Proxy(staticStyles, {
    get: (target: Record<string, CSSProperties>, name: string) => name in target
      ? {
          ...target[name],
          ...defaultStyle,
        }
      : defaultStyle,
  });
};

export default buildStyles;
