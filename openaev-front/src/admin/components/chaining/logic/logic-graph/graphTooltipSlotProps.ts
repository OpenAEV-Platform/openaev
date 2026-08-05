import { type Theme } from '@mui/material/styles';

// Graph node tooltips portal to <body> at MUI's default tooltip z-index (1500), which is ABOVE the
// Configure/Add drawer (z-index ~1202). Because the graph always lives behind that drawer, a
// lingering node or handle tooltip would float over the open drawer. Capping the popper just below
// the drawer keeps these tooltips above the graph but never over the drawer; tooltips rendered
// INSIDE the drawer keep the default z-index and stay visible as expected.
const graphTooltipSlotProps = { popper: { sx: { zIndex: (theme: Theme) => theme.zIndex.drawer - 1 } } };

export default graphTooltipSlotProps;
