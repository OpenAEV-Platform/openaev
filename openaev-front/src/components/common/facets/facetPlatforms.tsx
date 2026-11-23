import PlatformIcon from '../../PlatformIcon';

// The platforms a facet sidebar offers to filter on.
export const FACET_PLATFORMS = ['Windows', 'Linux', 'MacOS'];

export const platformFacetIcon = (platform: string) => <PlatformIcon platform={platform} width={16} />;
