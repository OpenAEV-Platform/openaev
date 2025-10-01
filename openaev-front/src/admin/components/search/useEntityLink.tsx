const useEntityLink = (entity: string, id: string, searchTerm: string) => {
  switch (entity) {
    case 'Asset':
      return `/admin/assets/endpoints?search=${searchTerm}&id=${id}`;
    case 'AssetGroup':
      return `/admin/assets/asset_groups?search=${searchTerm}&id=${id}`;
    case 'User':
      return `/admin/teams/players?search=${searchTerm}&id=${id}`;
    case 'Team':
      return `/admin/teams/teams?search=${searchTerm}&id=${id}`;
    case 'Organization':
      return `/admin/teams/organizations?search=${searchTerm}&id=${id}`;
    case 'Scenario':
      return `/admin/scenarios/${id}}`;
    case 'Exercise':
      return `/admin/simulations/${id}`;
    default:
      return ('');
  }
};

export default useEntityLink;
