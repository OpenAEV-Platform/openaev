import { type InjectStore } from '../../../../../actions/injects/Inject';
import { type Team } from '../../../../../utils/api-types';

/**
 * Representative SAMPLE datasets for the simulation Execution tab.
 *
 * Rendered (greyed, non-interactive, "Sample"-chipped via SamplePreview) when
 * the simulation has no injects or nothing has been sent yet, so the tab
 * always previews exactly what it will look like once the simulation runs.
 * Never persisted or sent anywhere.
 *
 * All sample injects use PAYLOAD types (Command, Executable...) so InjectIcon
 * resolves them to local icons instead of fetching injector images from the
 * backend for injectors that do not exist.
 */

export const sampleTimelineTeams: Team[] = [
  {
    team_id: 'sample-team-red',
    team_name: 'Red team',
  },
  {
    team_id: 'sample-team-soc',
    team_name: 'SOC analysts',
  },
] as unknown as Team[];

const timelineInject = (
  id: string,
  title: string,
  payloadType: string,
  teamId: string,
  dependsDuration: number,
  done: boolean,
): InjectStore => ({
  inject_id: id,
  inject_title: title,
  inject_enabled: true,
  inject_type: payloadType,
  inject_all_teams: false,
  inject_teams: [teamId],
  inject_depends_duration: dependsDuration,
  inject_injector_contract: { injector_contract_payload: { payload_type: payloadType } },
  inject_status: done ? { status_name: 'SUCCESS' } : null,
}) as unknown as InjectStore;

export const sampleTimelineInjects: InjectStore[] = [
  timelineInject('sample-tl-1', 'Initial access - phishing payload', 'Command', 'sample-team-red', 0, true),
  timelineInject('sample-tl-2', 'Privilege escalation', 'Executable', 'sample-team-red', 1800, true),
  timelineInject('sample-tl-3', 'Detection drill notification', 'Command', 'sample-team-soc', 2700, true),
  timelineInject('sample-tl-4', 'Credential dumping', 'Executable', 'sample-team-red', 5400, false),
  timelineInject('sample-tl-5', 'Network sweep', 'NetworkTraffic', 'sample-team-soc', 7200, false),
  timelineInject('sample-tl-6', 'DNS exfiltration attempt', 'DnsResolution', 'sample-team-red', 10800, false),
];

const flowInject = (
  id: string,
  title: string,
  payloadType: string,
  sentAt: string,
  status: string,
): InjectStore => ({
  inject_id: id,
  inject_title: title,
  inject_enabled: true,
  inject_type: payloadType,
  inject_injector_contract: { injector_contract_payload: { payload_type: payloadType } },
  inject_status: {
    status_name: status,
    tracking_sent_date: sentAt,
  },
}) as unknown as InjectStore;

// Built on demand so the sample window always ends "now".
export const sampleFlowInjects = (): InjectStore[] => {
  const now = Date.now();
  const minutesAgo = (minutes: number) => new Date(now - minutes * 60_000).toISOString();
  return [
    flowInject('sample-flow-1', 'Initial access - phishing payload', 'Command', minutesAgo(170), 'SUCCESS'),
    flowInject('sample-flow-2', 'Persistence - registry run key', 'Command', minutesAgo(150), 'SUCCESS'),
    flowInject('sample-flow-3', 'Credential dumping', 'Executable', minutesAgo(110), 'SUCCESS'),
    flowInject('sample-flow-4', 'Lateral movement - remote service', 'NetworkTraffic', minutesAgo(95), 'ERROR'),
    flowInject('sample-flow-5', 'File drop - staging directory', 'FileDrop', minutesAgo(60), 'SUCCESS'),
    flowInject('sample-flow-6', 'DNS exfiltration attempt', 'DnsResolution', minutesAgo(30), 'SUCCESS'),
    flowInject('sample-flow-7', 'Cleanup - artifact removal', 'Command', minutesAgo(10), 'SUCCESS'),
  ];
};
