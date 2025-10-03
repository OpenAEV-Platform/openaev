// General Names

export const INJECT = 'INJECT';
export const SIMULATION = 'SIMULATION';
export const SCENARIO = 'SCENARIO';

// Collectors
export const CROWDSTRIKE = 'openaev_crowdstrike';
export const DEFENDER = 'openaev_microsoft_defender';
export const SENTINEL = 'openaev_microsoft_sentinel';
export const SPLUNK = 'openaev_splunk_es';

export const COLLECTOR_LIST = [CROWDSTRIKE, SPLUNK, DEFENDER, SENTINEL];
export const COLLECTOR_LIST_AI = [CROWDSTRIKE, SPLUNK];
export const PAYLOAD_TYPE_LIST_AI = ['DnsResolution', 'Command'];
