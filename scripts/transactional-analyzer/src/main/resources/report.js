function expandMethodTree(e, id) {
  e.stopPropagation();
  e.preventDefault();
  const tree = document.getElementById(id);
  if (!tree) return;
  tree.open = true;
  tree.querySelectorAll('details').forEach(d => d.open = true);
}

function expandAll(scopeId) {
  const scope = document.getElementById(scopeId);
  if (!scope) return;
  scope.querySelectorAll('details').forEach(d => d.open = true);
}
function collapseAll(scopeId) {
  const scope = document.getElementById(scopeId);
  if (!scope) return;
  scope.querySelectorAll('details').forEach(d => d.open = false);
}
function expandAllGlobal() {
  document.querySelectorAll('.repo-section:not([style*="display: none"]) details').forEach(d => d.open = true);
}
function collapseAllGlobal() {
  document.querySelectorAll('.repo-section:not([style*="display: none"]) details').forEach(d => d.open = false);
}

// ── Overrides dropdown ──────────────────────────────────────────────────────
function toggleOverridesPanel(e) {
  e.stopPropagation();
  const panel = document.getElementById('od-panel');
  const chevron = document.getElementById('od-chevron');
  const open = panel.style.display === 'none';
  panel.style.display = open ? '' : 'none';
  chevron.textContent = open ? '▴' : '▾';
}

document.addEventListener('click', e => {
  const panel = document.getElementById('od-panel');
  if (panel && panel.style.display !== 'none') {
    panel.style.display = 'none';
    document.getElementById('od-chevron').textContent = '▾';
  }
});

function updateOdCount() {
  const all     = document.querySelectorAll('input[data-filter="override"]');
  const checked = document.querySelectorAll('input[data-filter="override"]:checked');
  document.getElementById('od-count').textContent = checked.length + ' / ' + all.length;
}

function selectGroup(filterName, checked) {
  document.querySelectorAll(`input[data-filter="${filterName}"]`).forEach(cb => cb.checked = checked);
  applyFilters();
}

function selectAllOverrides() {
  document.querySelectorAll('input[data-filter="override"]').forEach(cb => cb.checked = true);
  updateOdCount(); applyFilters();
}

function selectNoneOverrides() {
  document.querySelectorAll('input[data-filter="override"]').forEach(cb => cb.checked = false);
  updateOdCount(); applyFilters();
}

function filterOverridesSearch() {
  const q = document.getElementById('od-search').value.toLowerCase();
  document.querySelectorAll('#od-list .od-item').forEach(label => {
    const text = label.querySelector('.od-fqn')?.title?.toLowerCase() || '';
    label.style.display = text.includes(q) ? '' : 'none';
  });
}

// ── Filters ─────────────────────────────────────────────────────────────────
function getActiveFilters() {
  const tx = new Set(), ep = new Set(), ann = new Set();
  const overrides = new Set(), allOverrides = new Set();
  document.querySelectorAll('input[data-filter="tx"]:checked').forEach(cb => tx.add(cb.dataset.value));
  document.querySelectorAll('input[data-filter="ep"]:checked').forEach(cb => ep.add(cb.dataset.value));
  document.querySelectorAll('input[data-filter="ann"]:checked').forEach(cb => ann.add(cb.dataset.value));
  document.querySelectorAll('input[data-filter="override"]').forEach(cb => {
    allOverrides.add(cb.dataset.value);
    if (cb.checked) overrides.add(cb.dataset.value);
  });
  return { tx, ep, ann, overrides, allOverrides };
}

function applyFilters() {
  const { tx, ep, ann, overrides, allOverrides } = getActiveFilters();
  document.querySelectorAll('.repo-section').forEach(section => {
    const repoId = section.id;
    let visibleCount = 0;
    section.querySelectorAll('.method-tree').forEach(details => {
      // Global OR: show if the method matches ANY checked dimension
      const matchesTx = tx.has(details.dataset.tx);
      const matchesEp = ep.has(details.dataset.ep);
      const methodAnns = new Set(details.dataset.ann ? details.dataset.ann.split(',') : []);
      const matchesAnn = [...ann].some(a => methodAnns.has(a));
      const methodOverrides = (details.dataset.overrides || '')
          .split(',').map(s => s.trim()).filter(Boolean);
      const matchesOverride = methodOverrides.length > 0
          && methodOverrides.some(o => overrides.has(o));
      const show = matchesTx || matchesEp || matchesAnn || matchesOverride;
      details.style.display = show ? '' : 'none';
      if (show) visibleCount++;
    });
    const hide = visibleCount === 0;
    section.style.display = hide ? 'none' : '';
    const sidebarLink = document.querySelector(`#sidebar a[href="#${repoId}"]`);
    if (sidebarLink) {
      const li = sidebarLink.closest('li');
      if (li) li.style.display = hide ? 'none' : '';
    }
  });
  updateOdCount();
  applyInTreeFilter(ann, ep, overrides, allOverrides);
  // Re-render paths if paths view is active
  if (pathsViewActive) refreshPathsView();
}

function selectAllFilters() {
  document.querySelectorAll('input[data-filter]').forEach(cb => cb.checked = true);
  applyFilters();
}

function selectNoneFilters() {
  document.querySelectorAll('input[data-filter]').forEach(cb => cb.checked = false);
  applyFilters();
}

function initFilters() {
  document.querySelectorAll('input[data-filter]').forEach(cb => {
    cb.addEventListener('change', applyFilters);
  });
}

// ── In-tree node filtering ───────────────────────────────────────────────────

/**
 * Returns the annotation filter key for a .caller-node element,
 * or null for sentinel nodes (depth-limit, cyclic) that are always shown.
 */
function nodeAnnType(node) {
  for (const cls of ['tx-spring', 'tx-jakarta', 'tx-indirect', 'no-tx', 'external']) {
    if (node.classList.contains(cls)) return cls;
  }
  if (node.classList.contains('cyclic')) return 'cyclic';
  return null; // depth-limit or unknown → always visible
}

/**
 * Returns true if this node matches any active in-tree filter dimension.
 * Sentinel nodes (cyclic, depth-limit) always match.
 * A dimension is "active" when it has a partial selection (0 < checked < total).
 */
function nodeMatchesFilters(node, ann, annActive, ep, epActive, overrides, overridesActive) {
  // Sentinels are always visible
  if (node.classList.contains('cyclic') || node.classList.contains('depth-limit')) return true;

  if (annActive) {
    const type = nodeAnnType(node);
    if (type !== null && ann.has(type)) return true;
  }

  // ep: node is itself an endpoint and the ep dimension is partially filtered
  if (epActive && node.dataset.ep === 'true') return true;

  // overrides: node implements/overrides one of the selected types
  if (overridesActive) {
    const nodeOverrides = (node.dataset.overrides || '').split(',').map(s => s.trim()).filter(Boolean);
    if (nodeOverrides.some(o => overrides.has(o))) return true;
  }

  return false;
}

/**
 * Bottom-up recursive walk. A node is shown if it matches OR any descendant matches.
 * Returns true if any child in this container ended up visible.
 */
function filterCallerNodes(container, ann, annActive, ep, epActive, overrides, overridesActive) {
  if (!container) return false;
  let anyVisible = false;
  Array.from(container.children).forEach(child => {
    if (!child.classList.contains('caller-node')) return;
    const subCallers = child.querySelector('.sub-callers');
    const descendantVisible = filterCallerNodes(subCallers, ann, annActive, ep, epActive, overrides, overridesActive);
    const selfMatches = nodeMatchesFilters(child, ann, annActive, ep, epActive, overrides, overridesActive);
    const show = selfMatches || descendantVisible;
    child.classList.toggle('node-filtered', !show);
    if (show) anyVisible = true;
  });
  return anyVisible;
}

function applyInTreeFilter(ann, ep, overrides, allOverrides) {
  // Clear previous in-tree filtering
  document.querySelectorAll('.caller-node.node-filtered')
      .forEach(n => n.classList.remove('node-filtered'));

  if (!document.getElementById('in-tree-filter')?.checked) return;

  const totalAnn = document.querySelectorAll('input[data-filter="ann"]').length;
  const annActive    = ann.size > 0 && ann.size < totalAnn;
  const epActive     = ep.size > 0 && ep.size < 3;
  const overridesActive = overrides.size > 0 && overrides.size < allOverrides.size;

  // No dimension is actively filtering → nothing to do
  if (!annActive && !epActive && !overridesActive) return;

  document.querySelectorAll('.method-tree').forEach(tree => {
    if (tree.style.display === 'none') return;
    filterCallerNodes(tree.querySelector('.method-tree-body'),
        ann, annActive, ep, epActive, overrides, overridesActive);
  });
}

// ── Paths view ───────────────────────────────────────────────────────────────

let pathsViewActive = false;

const NODE_CLASSES = ['tx-spring','tx-jakarta','tx-indirect','no-tx','repo-method','external','cyclic','depth-limit'];

function togglePathsView() {
  pathsViewActive = !pathsViewActive;
  const btn = document.getElementById('paths-view-btn');
  btn.textContent = pathsViewActive ? 'Tree view' : 'Paths view';
  btn.classList.toggle('global-btn-active', pathsViewActive);
  refreshPathsView();
}

function refreshPathsView() {
  document.querySelectorAll('.method-tree').forEach(tree => {
    if (tree.style.display === 'none') return;
    // Invalidate cached paths container so it re-renders with current filter state
    const old = tree.querySelector('.paths-container');
    if (old) old.remove();
    const body = tree.querySelector('.method-tree-body');
    if (pathsViewActive) {
      if (body) body.style.display = 'none';
      tree.appendChild(buildPathsContainer(tree));
    } else {
      if (body) body.style.display = '';
    }
  });
}

function extractNodeInfo(callerNode) {
  return {
    className:   callerNode.querySelector('.node-class')?.textContent  || '',
    methodName:  callerNode.querySelector('.node-method')?.textContent || '',
    cssClass:    [...callerNode.classList].find(c => NODE_CLASSES.includes(c)) || '',
    isCyclic:    callerNode.classList.contains('cyclic'),
    isDepthLimit: callerNode.classList.contains('depth-limit'),
    overrides:   callerNode.dataset.overrides || '',
  };
}

/** DFS — collects all root-to-leaf paths (entry point → direct caller of repo method). */
function collectPaths(container) {
  const paths = [];
  function dfs(node, pathSoFar) {
    if (node.classList.contains('node-filtered')) return;
    const info = extractNodeInfo(node);
    const path = [...pathSoFar, info];
    const subCallers = node.querySelector(':scope > .sub-callers');
    const visibleChildren = subCallers
      ? Array.from(subCallers.children).filter(c =>
          c.classList.contains('caller-node') && !c.classList.contains('node-filtered'))
      : [];
    if (visibleChildren.length === 0 || info.isCyclic || info.isDepthLimit) {
      paths.push(path);
    } else {
      visibleChildren.forEach(child => dfs(child, path));
    }
  }
  Array.from(container.children)
    .filter(c => c.classList.contains('caller-node') && !c.classList.contains('node-filtered'))
    .forEach(node => dfs(node, []));
  return paths;
}

function buildPathsContainer(tree) {
  const container = document.createElement('div');
  container.className = 'paths-container';

  const body = tree.querySelector('.method-tree-body');
  const repoSig = tree.querySelector('.method-sig')?.textContent?.trim() || '';

  if (!body) {
    container.innerHTML = '<p class="empty-note">No callers found in indexed source.</p>';
    return container;
  }

  const paths = collectPaths(body);

  if (paths.length === 0) {
    container.innerHTML = '<p class="empty-note">No callers found in indexed source.</p>';
    return container;
  }

  // Repo method node — leaf at the bottom of every path
  const repoNodeInfo = { className: '', methodName: repoSig, cssClass: 'repo-method',
                         isCyclic: false, isDepthLimit: false, overrides: '' };

  // Build trie: paths are [directCaller, ..., entryPoint]; reverse → [entryPoint, ..., repo]
  const trie = buildPathTrie(paths, repoNodeInfo);
  renderTrieChildren(trie, container);
  return container;
}

// ── Path trie ────────────────────────────────────────────────────────────────

/** Inserts all reversed paths into a prefix-trie keyed by className+methodName only.
 *  cssClass is intentionally excluded: the same method can appear with different classes
 *  in different subtree contexts (e.g. no-tx vs tx-indirect), and we still want to merge. */
function buildPathTrie(paths, repoNodeInfo) {
  const root = { info: null, children: new Map() };
  paths.forEach(path => {
    // path is [directCaller, ..., entryPoint]; reverse to get natural call order
    const nodes = [...path].reverse();
    nodes.push(repoNodeInfo);
    let cur = root;
    nodes.forEach(info => {
      const key = info.className + '\x00' + info.methodName;
      if (!cur.children.has(key)) cur.children.set(key, { info, children: new Map() });
      cur = cur.children.get(key);
    });
  });
  return root;
}

/** Renders trie children into a container element. */
function renderTrieChildren(trieNode, container) {
  trieNode.children.forEach(child => container.appendChild(renderTrieNode(child)));
}

/** Recursively renders a trie node as a .caller-node details/div matching existing tree CSS. */
function renderTrieNode(node) {
  const hasChildren = node.children.size > 0;
  const info = node.info;

  if (hasChildren) {
    const details = document.createElement('details');
    details.className = 'caller-node ' + info.cssClass;
    details.open = false;

    const summary = document.createElement('summary');
    appendNodeLabel(summary, info);
    const cnt = document.createElement('span');
    cnt.className = 'caller-count';
    cnt.textContent = `▸ ${node.children.size} call${node.children.size === 1 ? '' : 's'}`;
    summary.appendChild(cnt);
    details.appendChild(summary);

    const sub = document.createElement('div');
    sub.className = 'sub-callers';
    renderTrieChildren(node, sub);
    details.appendChild(sub);
    return details;
  } else {
    const div = document.createElement('div');
    div.className = 'caller-node leaf ' + info.cssClass;
    appendNodeLabel(div, info);
    return div;
  }
}

/** Appends node-class / node-method spans (matches FTL nodeContent macro output). */
function appendNodeLabel(parent, info) {
  if (info.isCyclic) {
    parent.appendChild(Object.assign(document.createElement('span'), { className: 'cycle-marker', textContent: '↻' }));
  } else if (info.isDepthLimit) {
    parent.appendChild(Object.assign(document.createElement('span'), { className: 'depth-limit-marker', textContent: '⋯' }));
    parent.appendChild(document.createTextNode(' depth limit reached'));
    return;
  }
  if (info.className) {
    parent.appendChild(Object.assign(document.createElement('span'), { className: 'node-class', textContent: info.className }));
    parent.appendChild(document.createTextNode('.'));
  }
  parent.appendChild(Object.assign(document.createElement('span'), { className: 'node-method', textContent: info.methodName }));
  if (info.overrides) {
    const b = document.createElement('span');
    b.className = 'badge overrides-from';
    b.title = 'Implements / overrides from ' + info.overrides.replace(/,/g, ', ');
    b.textContent = '◁ ' + info.overrides.replace(/,/g, ', ');
    parent.appendChild(b);
  }
}

// ── Sidebar search ───────────────────────────────────────────────────────────
function initSearch() {
  const input = document.getElementById('search-input');
  if (!input) return;
  input.addEventListener('input', () => {
    const q = input.value.trim().toLowerCase();
    document.querySelectorAll('.caller-node').forEach(node => {
      node.classList.remove('search-highlight', 'search-hidden');
    });
    if (!q) return;
    document.querySelectorAll('.caller-node').forEach(node => {
      const text = (node.querySelector('.node-class')?.textContent || '') +
                   (node.querySelector('.node-method')?.textContent || '') +
                   (node.querySelector('.file-ref')?.title || '');
      if (text.toLowerCase().includes(q)) {
        node.classList.add('search-highlight');
        let p = node.parentElement;
        while (p) {
          if (p.tagName === 'DETAILS') p.open = true;
          p = p.parentElement;
        }
      }
    });
  });
}

// ── Keyboard shortcut: / to focus search ────────────────────────────────────
document.addEventListener('keydown', e => {
  if (e.key === '/' && document.activeElement.tagName !== 'INPUT') {
    e.preventDefault();
    document.getElementById('search-input')?.focus();
  }
});

document.addEventListener('DOMContentLoaded', () => {
  initFilters();
  document.getElementById('overrides-dropdown')
      .addEventListener('click', e => e.stopPropagation());
  const sidebar = document.getElementById('sidebar');
  if (sidebar) {
    const wrap = document.createElement('div');
    wrap.id = 'search-wrap';
    wrap.innerHTML = '<input id="search-input" type="text" placeholder="Search class or method… (/)" autocomplete="off">';
    sidebar.insertBefore(wrap, sidebar.firstChild.nextSibling);
    initSearch();
  }
});
