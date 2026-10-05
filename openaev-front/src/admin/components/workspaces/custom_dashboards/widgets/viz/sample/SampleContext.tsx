import { createContext, useContext } from 'react';

/**
 * A widget card owns a title row, and the "Sample" marker belongs in its
 * top-right corner, level with the title — not floating over the chart. The
 * body cannot reach that row, so it reports the sample state upward and the
 * card renders the marker. Outside a card (a detail-page panel, say) there is
 * no provider and `SamplePreview` keeps drawing its own marker.
 */
export const SampleReportContext = createContext<((active: boolean) => void) | null>(null);

export const useReportSample = () => useContext(SampleReportContext);
