import {
  ASSET_BASE_URL,
  ASSET_GROUP_BASE_URL,
  ORGANIZATION_BASE_URL,
  PERSON_BASE_URL,
  SCENARIO_BASE_URL,
  SECURITY_PLATFORM_BASE_URL,
  SIMULATION_BASE_URL,
  TEAM_BASE_URL,
} from '../../../constants/BaseUrls';

// Maps a search result class to its canonical overview page: every searchable
// entity now has a dedicated overview, so results must never land on a list
// page with an edit drawer. Returns null for unsupported classes so callers
// can render a non-navigable row instead of a dead link.
const useEntityLink = (entity: string, id: string): string | null => {
  switch (entity) {
    case 'Asset':
      return `${ASSET_BASE_URL}/${id}`;
    case 'AssetGroup':
      return `${ASSET_GROUP_BASE_URL}/${id}`;
    case 'SecurityPlatform':
      return `${SECURITY_PLATFORM_BASE_URL}/${id}`;
    case 'User':
      return `${PERSON_BASE_URL}/${id}`;
    case 'Team':
      return `${TEAM_BASE_URL}/${id}`;
    case 'Organization':
      return `${ORGANIZATION_BASE_URL}/${id}`;
    case 'Scenario':
      return `${SCENARIO_BASE_URL}/${id}`;
    case 'Exercise':
      return `${SIMULATION_BASE_URL}/${id}`;
    default:
      return null;
  }
};

export default useEntityLink;
