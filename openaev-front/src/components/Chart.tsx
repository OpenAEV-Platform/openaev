import { type ComponentProps, lazy, Suspense } from 'react';

// Lazy wrapper around react-apexcharts: splits the heavy apexcharts bundle out of the main chunk
// so screens without charts do not pay for it. Drop-in replacement for the static import.
const ApexChart = lazy(() => import('react-apexcharts'));

const Chart = (props: ComponentProps<typeof ApexChart>) => (
  <Suspense fallback={null}>
    <ApexChart {...props} />
  </Suspense>
);

export default Chart;
