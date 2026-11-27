// docs/links.json is the single map of documentation links shared with the backend. The docs
// build checks every path in it against the built site (`yarn check-docs-links`), so code refers
// to a page by its key and an unknown key fails `yarn check-ts`. This is the only module that
// imports the map: named imports keep the rest of the file, such as the `retired` paths, out of
// the bundle.
import { base, links } from '../../../docs/links.json';

export type DocsLinkKey = keyof typeof links;

export const docsBase: string = base;

export const docsLinkKeys = Object.keys(links) as DocsLinkKey[];

const docsUrl = (key: DocsLinkKey): string => `${base}${links[key]}`;

export default docsUrl;
