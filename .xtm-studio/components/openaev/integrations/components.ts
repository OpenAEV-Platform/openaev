// The collectors and injectors of OpenAEV, one component each, generated from what their
// repositories hold: each one's manifest-metadata.json, and the settings the catalog of OpenAEV
// declares for it, in catalog-integrators.json of the platform's repository. Each runs at the
// version of the OpenAEV platform's descriptor, and reaches the platform through its contract
// openaev; an injector also reaches the RabbitMQ the platform gives it when it registers.
//
// It reads the catalog of this worktree, and the collectors and injectors repositories in their
// default worktrees, beside this one in code/.
//
// Answers XTM Studio, on its standard output: { schema: 1, inputs, components }.

import { access, readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';

/** This worktree and its organization, from this directory: <worktree>/.xtm-studio/components/openaev/integrations. */
const REPOSITORY = '../../../..';
const ORG = '../../../../..';
const CATALOG = join(REPOSITORY, 'openaev-api/src/main/resources/catalog/catalog-integrators.json');
const PLATFORM = '../platform/component.yaml';
const KINDS = [
  { kind: 'collector', repository: join(ORG, 'collectors'), prefix: 'COLLECTOR' },
  { kind: 'injector', repository: join(ORG, 'injectors'), prefix: 'INJECTOR' },
] as const;

interface Property {
  default?: unknown;
  description?: string;
  format?: string;
  writeOnly?: boolean;
  deprecated?: boolean;
}

interface Metadata {
  title: string;
  short_description?: string;
  container_image: string;
}

interface Contract {
  container_image: string;
  config_schema?: { properties?: Record<string, Property>; required?: string[] };
}

const exists = async (path: string) => {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
};

/** Whether a setting is a secret: its schema says so, or its name does. */
const isSecret = (name: string, property: Property) =>
  property.format === 'password' || property.writeOnly === true || /(PASSWORD|SECRET|TOKEN|API_KEY|APIKEY|_KEY$|PRIVATE)/.test(name);

/** The icon of a collector or an injector, in the img/ of its package, as its settings name it. */
const iconOf = async (dir: string): Promise<string | undefined> => {
  const files = (await readdir(dir, { recursive: true })).filter((path) => /(^|\/)img\/[^/]+\.(png|svg|jpg)$/.test(path) && !/(^|\/)(tests?|node_modules)\//.test(path));
  return files.toSorted((a, b) => a.length - b.length)[0];
};

const version = /^image:\s*\{\s*version:\s*([^\s}]+)\s*\}/m.exec(await readFile(PLATFORM, 'utf8'))?.[1];
if (!version) throw new Error(`${PLATFORM}: no image: { version: … }`);
const catalog = new Map(((JSON.parse(await readFile(CATALOG, 'utf8')) as { contracts: Contract[] }).contracts).map((contract) => [contract.container_image, contract]));

const inputs = [CATALOG, PLATFORM];
const components = [];
for (const { kind, repository, prefix } of KINDS) {
  for (const entry of (await readdir(repository, { withFileTypes: true })).filter((each) => each.isDirectory()).toSorted((a, b) => a.name.localeCompare(b.name))) {
    const file = join(repository, entry.name, 'manifest-metadata.json');
    if (entry.name === 'template' || !(await exists(file))) continue;
    inputs.push(file);
    const metadata = JSON.parse(await readFile(file, 'utf8')) as Metadata;
    const name = metadata.container_image;
    const schema = catalog.get(name)?.config_schema;
    const properties = schema?.properties ?? {};
    const required = new Set(schema?.required ?? []);
    const given = new Set(['OPENAEV_URL', 'OPENAEV_TOKEN', 'OPENAEV_TENANT_ID', `${prefix}_ID`, `${prefix}_NAME`, `${prefix}_LOG_LEVEL`]);
    const environment: Record<string, string> = {
      OPENAEV_URL: '{{requires.openaev.contract.url.value}}',
      OPENAEV_TOKEN: '{{requires.openaev.output.token}}',
      [`${prefix}_ID`]: '{{secrets.ID}}',
      [`${prefix}_NAME`]: String(properties[`${prefix}_NAME`]?.default ?? metadata.title),
      [`${prefix}_LOG_LEVEL`]: 'info',
    };
    const variables: Record<string, { description?: string; secret?: boolean; required?: boolean }> = {};
    for (const [key, property] of Object.entries(properties)) {
      if (given.has(key) || property.deprecated) continue;
      variables[key] = {
        ...(property.description ? { description: property.description } : {}),
        ...(isSecret(key, property) ? { secret: true } : {}),
        ...(required.has(key) && (property.default === undefined || property.default === null) ? { required: true } : {}),
      };
    }
    const icon = await iconOf(join(repository, entry.name));
    components.push({
      schema: 1,
      name,
      group: `openaev-${kind}s`,
      title: metadata.title,
      description: `${metadata.short_description ?? metadata.title}${schema ? '' : ' Its settings have no schema: see its README.'}`,
      logo: icon ? join(repository, entry.name, icon) : { icon: 'box' },
      image: { version },
      secrets: { ID: { type: 'uuid' } },
      environment,
      variables,
      requires: { openaev: { count: '1' } },
    });
  }
}

process.stdout.write(JSON.stringify({ schema: 1, inputs, components }));
