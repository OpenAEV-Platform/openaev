// Node dimensions for the attack-path graph. Kept out of the component files so each node module
// only exports its React component — otherwise Vite's React Fast Refresh breaks on HMR (a component
// module that also exports a constant can end up "not providing its default export").
export const AP_ENDPOINT_SIZE = 96;
export const AP_ENDPOINT_CLUSTER_SIZE = 84;
export const AP_FINDING_SIZE = 56;
// Injector node is deliberately a bit smaller than the endpoint (96) so the endpoints read as the
// primary targets on the map.
export const AP_INJECTOR_SIZE = 72;
