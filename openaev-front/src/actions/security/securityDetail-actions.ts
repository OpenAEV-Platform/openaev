import { simpleCall, simplePostCall } from '../../utils/Action';

// Read-only fetch-by-id helpers used by the Security detail/overview pages
// (Users, Roles, Groups, Organizations). They return the raw output DTOs and do
// not go through the Redux referential, so detail pages own their own state.

export const fetchUserById = (userId: string) => {
  return simpleCall(`/api/users/${userId}`);
};

export const fetchRoleById = (roleId: string) => {
  return simpleCall(`/api/roles/${roleId}`);
};

export const fetchGroupById = (groupId: string) => {
  return simpleCall(`/api/groups/${groupId}`);
};

export const fetchOrganizationById = (organizationId: string) => {
  return simpleCall(`/api/organizations/${organizationId}`);
};

export const fetchAllRoles = () => {
  return simpleCall('/api/roles');
};

// Groups are only exposed through the search endpoint (there is no GET /api/groups),
// so the detail pages page through a single large request to build a complete list.
export const fetchAllGroups = () => {
  return simplePostCall('/api/groups/search', {
    page: 0,
    size: 1000,
    filterGroup: {
      mode: 'and',
      filters: [],
    },
  });
};
