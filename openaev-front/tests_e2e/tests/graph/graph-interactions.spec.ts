import { type APIRequestContext, expect, type Page } from '@playwright/test';

import { test } from '../../fixtures';
import { tenantApiPath, tenantUrl } from '../../utils/url';

/**
 * The exploration graph answers its toolbar on the FIRST click (#9572). A layout button used to press
 * itself and write the layout in the URL while every node stayed where it was: the layout plan was
 * written into refs during the render, and the StrictMode replay of that render read the run as
 * already done. Each test also fails on any error or warning the page logs, so a console error the
 * graph raises (a setState during render, a duplicate key, a frame read out of range) is a red test.
 */

interface Seeded {
  groupId: string;
  assetIds: string[];
}

const createEndpoint = async (request: APIRequestContext, name: string): Promise<string> => {
  const response = await request.post(tenantApiPath('/api/endpoints/agentless'), {
    data: {
      asset_name: name,
      endpoint_platform: 'Linux',
      endpoint_arch: 'x86_64',
      endpoint_hostname: name,
    },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return ((await response.json()) as { asset_id: string }).asset_id;
};

/** An asset group with four endpoints: a focused view of five nodes. */
const seed = async (request: APIRequestContext, tag: string): Promise<Seeded> => {
  const assetIds = await Promise.all(['a', 'b', 'c', 'd'].map(name => createEndpoint(request, `graph-e2e-${tag}-${name}`)));
  const group = await request.post(tenantApiPath('/api/asset_groups'), { data: { asset_group_name: `graph-e2e-${tag}` } });
  expect(group.ok(), await group.text()).toBeTruthy();
  const groupId = ((await group.json()) as { asset_group_id: string }).asset_group_id;
  // The creation ignores the members: they are set on their own endpoint.
  const members = await request.put(tenantApiPath(`/api/asset_groups/${groupId}/assets`), { data: { asset_group_assets: assetIds } });
  expect(members.ok(), await members.text()).toBeTruthy();
  return {
    groupId,
    assetIds,
  };
};

/** Every node's transform, by node id, serialised: what the user sees move. */
const positions = (page: Page) => page.locator('.react-flow__node').evaluateAll(nodes => JSON.stringify(Object.fromEntries(
  nodes.map(n => [(n as HTMLElement).dataset.id, (n as HTMLElement).style.transform]),
)));

/** Waits until the nodes stop moving (two reads a beat apart agree) and answers where they are. */
const settled = async (page: Page) => {
  let last = '';
  await expect.poll(async () => {
    const now = await positions(page);
    const still = now === last;
    last = now;
    return still;
  }, { intervals: [250] }).toBe(true);
  return last;
};

/** The console listener: any error, warning or uncaught exception of the page fails the test. */
const watchConsole = (page: Page) => {
  const problems: string[] = [];
  page.on('console', (message) => {
    const text = message.text();
    // xyflow's notice about the hidden attribution is a licence notice, not a defect of the page.
    if ((message.type() === 'error' || message.type() === 'warning') && !text.includes('reactflow.dev/remove-attr')) {
      problems.push(`${message.type()}: ${text}`);
    }
  });
  page.on('pageerror', (error) => {
    // A browser notice, raised when a resize observer settles across frames: not an application error.
    if (!error.message.includes('ResizeObserver loop')) {
      problems.push(`pageerror: ${error.message}`);
    }
  });
  return problems;
};

/** One click on a layout button: it is pressed and the nodes leave their place, then settle. */
const switchLayout = async (page: Page, name: string) => {
  const before = await settled(page);
  const button = page.getByTestId('graph-canvas-toolbar').getByRole('button', { name: new RegExp(`^${name}`) });
  await button.click();
  await expect(button).toHaveAttribute('aria-pressed', 'true');
  await expect.poll(() => positions(page), { message: `${name}: the nodes move after one click` }).not.toBe(before);
  await settled(page);
};

test.describe('Exploration graph interactions', () => {
  let seeded: Seeded;

  test.beforeAll(async ({ request }) => {
    seeded = await seed(request, `${Date.now()}`);
  });

  test('each layout button moves the nodes on the first click, and Re-run plays the layout again', async ({ page }) => {
    const problems = watchConsole(page);
    await page.goto(tenantUrl(`/admin/graph?focus=asset_group:${seeded.groupId}`));
    await expect(page.locator('.react-flow__node')).toHaveCount(5);

    await switchLayout(page, 'Hierarchical layout');
    await switchLayout(page, 'Radial layout');
    await switchLayout(page, 'Force layout');

    // Re-run plays the force simulation from its seed: the nodes leave their place, then come back.
    const force = await settled(page);
    await page.getByTestId('graph-canvas-toolbar').getByRole('button', { name: 'Re-run the layout' }).click();
    await expect.poll(() => positions(page), { message: 'Re-run animates the layout' }).not.toBe(force);
    expect(await settled(page)).toBe(force);

    expect(problems).toEqual([]);
  });

  test('a click selects one node alone, the canvas and Esc clear it, Ctrl+click adds to it', async ({ page }) => {
    const problems = watchConsole(page);
    await page.goto(tenantUrl(`/admin/graph?focus=asset_group:${seeded.groupId}`));
    await expect(page.locator('.react-flow__node')).toHaveCount(5);
    await settled(page);
    const node = (index: number) => page.locator(`.react-flow__node[data-id="ASSET:${seeded.assetIds[index]}"]`);
    const ringed = page.locator('[data-testid^="graph-node-"][data-selected="true"]');
    // The persistent Drawer's root is a zero-size docked box (its paper is absolutely positioned): assert on the paper.
    const panel = page.getByTestId('graph-side-panel').locator('.MuiDrawer-paper');

    // The focus anchor has its own mark, never the selection ring.
    await expect(page.locator('[data-testid^="graph-node-"][data-anchor="true"]')).toHaveCount(1);

    // A plain click on A then on B: B alone is selected (OpenCTI's graph).
    await node(0).click();
    await expect(ringed).toHaveCount(1);
    await node(1).click();
    await expect(ringed).toHaveCount(1);
    await expect(node(1).locator('[data-selected="true"]')).toHaveCount(1);
    await expect(panel).toBeVisible();

    // A click on the empty canvas clears the selection and closes the panel.
    const pane = await page.locator('.react-flow__pane').boundingBox();
    await page.mouse.click((pane?.x ?? 0) + 12, (pane?.y ?? 0) + (pane?.height ?? 400) / 2);
    await expect(ringed).toHaveCount(0);
    await expect(panel).toHaveCount(0);

    // Esc does the same.
    await node(2).click();
    await expect(ringed).toHaveCount(1);
    await page.keyboard.press('Escape');
    await expect(ringed).toHaveCount(0);
    await expect(panel).toHaveCount(0);

    // Ctrl+click adds a node to the selection: both stay selected.
    await node(0).click();
    await node(3).click({ modifiers: ['Control'] });
    await expect(ringed).toHaveCount(2);

    expect(problems).toEqual([]);
  });

  test('an expand keeps the nodes on screen still and undo restores the view', async ({ page }) => {
    const problems = watchConsole(page);
    await page.goto(tenantUrl(`/admin/graph?focus=asset_group:${seeded.groupId}`));
    await expect(page.locator('.react-flow__node')).toHaveCount(5);
    const before = JSON.parse(await settled(page)) as Record<string, string>;

    await page.locator(`.react-flow__node[data-id="ASSET:${seeded.assetIds[0]}"]`).dblclick();
    const after = JSON.parse(await settled(page)) as Record<string, string>;
    Object.keys(before).forEach(id => expect(after[id], `${id} stays where it was`).toBe(before[id]));

    await page.getByTestId('graph-canvas-toolbar').getByRole('button', { name: 'Undo' }).click();
    await expect(page.locator('.react-flow__node')).toHaveCount(5);

    expect(problems).toEqual([]);
  });

  test('each node shows its connections in the data, the badge equals the API count', async ({ page, request }) => {
    const problems = watchConsole(page);
    const response = await request.get(tenantApiPath(`/api/graph/asset_group/${seeded.groupId}/neighbourhood`));
    expect(response.ok()).toBeTruthy();
    const slice = (await response.json()) as {
      graph_nodes: {
        node_id: string;
        node_relation_counts?: Record<string, number> | null;
      }[];
    };
    const groupNode = slice.graph_nodes.find(n => n.node_id === `ASSET_GROUP:${seeded.groupId}`);
    const apiTotal = Object.values(groupNode?.node_relation_counts ?? {}).reduce((sum, n) => sum + n, 0);
    expect(apiTotal).toBe(4);

    await page.goto(tenantUrl(`/admin/graph?focus=asset_group:${seeded.groupId}`));
    await expect(page.locator('.react-flow__node')).toHaveCount(5);
    // The group draws its 4 members: 4 connections, none left to expand.
    const groupBadge = page.getByTestId(`graph-node-connections-ASSET_GROUP:${seeded.groupId}`);
    await expect(groupBadge).toHaveAttribute('data-total', String(apiTotal));
    await expect(groupBadge).toHaveAttribute('data-hidden', '0');
    // Each member draws its one group membership.
    const memberBadge = page.getByTestId(`graph-node-connections-ASSET:${seeded.assetIds[0]}`);
    await expect(memberBadge).toHaveAttribute('data-total', '1');

    expect(problems).toEqual([]);
  });

  test('the OpenCTI keyboard shortcuts: Ctrl+A selects every node, + zooms, Shift+F frames the selection', async ({ page }) => {
    const problems = watchConsole(page);
    await page.goto(tenantUrl(`/admin/graph?focus=asset_group:${seeded.groupId}`));
    await expect(page.locator('.react-flow__node')).toHaveCount(5);
    await settled(page);
    const ringed = page.locator('[data-testid^="graph-node-"][data-selected="true"]');
    const viewport = () => page.locator('.react-flow__viewport').evaluate(v => (v as HTMLElement).style.transform);

    // A click puts the focus in the graph; Ctrl+A then selects the five nodes.
    await page.locator(`.react-flow__node[data-id="ASSET:${seeded.assetIds[0]}"]`).click();
    await page.keyboard.press('Control+a');
    await expect(ringed).toHaveCount(5);

    const before = await viewport();
    await page.keyboard.press('+');
    await expect.poll(viewport, { message: '+ zooms in' }).not.toBe(before);

    const zoomed = await viewport();
    await page.keyboard.press('Escape');
    await page.locator(`.react-flow__node[data-id="ASSET:${seeded.assetIds[1]}"]`).click();
    await page.keyboard.press('Shift+F');
    await expect.poll(viewport, { message: 'Shift+F frames the selected node' }).not.toBe(zoomed);

    // G folds the legend and unfolds it again, as OpenCTI's toggleLegend.
    const legend = page.getByRole('group', { name: 'Legend' });
    await expect(legend).toBeVisible();
    await page.keyboard.press('g');
    await expect(legend).toHaveCount(0);
    await page.keyboard.press('g');
    await expect(legend).toBeVisible();

    expect(problems).toEqual([]);
  });

  test('the OpenCTI context menu actions: select by type, zoom to the node, path between two selected nodes', async ({ page }) => {
    const problems = watchConsole(page);
    await page.goto(tenantUrl(`/admin/graph?focus=asset_group:${seeded.groupId}`));
    await expect(page.locator('.react-flow__node')).toHaveCount(5);
    await settled(page);
    const ringed = page.locator('[data-testid^="graph-node-"][data-selected="true"]');
    const node = (index: number) => page.locator(`.react-flow__node[data-id="ASSET:${seeded.assetIds[index]}"]`);
    const viewport = () => page.locator('.react-flow__viewport').evaluate(v => (v as HTMLElement).style.transform);

    // Right click on an endpoint: every endpoint (4 of the 5 nodes, the group left out) is selected.
    await node(0).click({ button: 'right' });
    await page.getByRole('menuitem', { name: 'Select every node of this type' }).click();
    await expect(ringed).toHaveCount(4);

    // A plain click selects one node alone; Ctrl+click adds a second; the selection menu offers the path between them.
    await node(0).click();
    await node(1).click({ modifiers: ['Control'] });
    await node(1).click({ button: 'right' });
    await expect(page.getByRole('menuitem', { name: 'Find the path between them' })).toBeVisible();
    await page.keyboard.press('Escape');

    // Zoom to this node changes the viewport.
    const before = await viewport();
    await node(2).click({ button: 'right' });
    await page.getByRole('menuitem', { name: 'Zoom to this node' }).click();
    await expect.poll(viewport, { message: 'Zoom to this node frames it' }).not.toBe(before);

    expect(problems).toEqual([]);
  });
});
