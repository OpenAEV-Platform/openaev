<#-- ─────────────────────────────────────────────────────────────────────────
     Macros — defined first; they output nothing until called.
     ──────────────────────────────────────────────────────────────────────── -->

<#-- Renders a single caller-tree node recursively. -->
<#macro node n>
  <#if n.cyclic>
    <div class="caller-node cyclic">
      <span class="cycle-marker">↻</span>
      <span class="node-class">${n.className?html}</span>.<span class="node-method">${n.methodName?html}()</span>
      <span class="badge cyclic">cycle</span>
    </div>
  <#elseif n.depthLimit>
    <div class="caller-node depth-limit">
      <span class="depth-limit-marker">⋯</span> depth limit reached
    </div>
  <#elseif n.children?has_content>
    <details class="caller-node ${n.cssClass}"
             data-ep="${n.isEndpoint?string('true','false')}"
             data-overrides="${(n.overridesFrom!'')?html}">
      <summary>
        <@nodeContent n/>
        <span class="caller-count">▸ ${n.children?size} caller${(n.children?size == 1)?then("", "s")}</span>
      </summary>
      <div class="sub-callers">
        <#list n.children as child><@node child/></#list>
      </div>
    </details>
  <#else>
    <div class="caller-node leaf ${n.cssClass}"
         data-ep="${n.isEndpoint?string('true','false')}"
         data-overrides="${(n.overridesFrom!'')?html}"><@nodeContent n/></div>
  </#if>
</#macro>

<#-- Renders the class.method() name, override badge, annotation badges and file ref. -->
<#macro nodeContent n>
  <span class="node-class">${n.className?html}</span>.<span class="node-method">${n.methodName?html}()</span>
  <#if n.overridesFrom?has_content>
    <#assign overrideDisplay = n.overridesFrom?replace(",", ", ")>
    <span class="badge overrides-from" title="Implements / overrides from ${overrideDisplay?html}">◁ ${overrideDisplay?html}</span>
  </#if>
  <#list n.badges as b>
    <span class="badge ${b.cssClass}">${b.text}</span>
  </#list>
  <#if n.hasSource && n.lineNumber gt 0>
    <span class="file-ref" title="${n.filePath?html}">:${n.lineNumber}</span>
  <#elseif !n.hasSource>
    <span class="file-ref external-note">external</span>
  </#if>
</#macro>

<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Transactional Analyzer — ${date}</title>
  <style>
${css}
  </style>
</head>
<body>

  <nav id="sidebar">
    <div class="sidebar-title">📊 Tx Analyzer</div>
    <ul class="sidebar-list">
      <li class="sidebar-sep">Repositories (${repos?size})</li>
      <#list repos as repo>
        <li>
          <a href="#${repo.id}" class="sidebar-link">
            <span class="tx-dot sidebar-dot ${repo.txCssClass}" title="${repo.txTooltip?html}"></span>
            <span class="tx-dot sidebar-dot ${repo.epCssClass}" title="${repo.epTooltip?html}"></span>
            ${repo.simpleName?html}
          </a>
          <#if repo.methods?has_content>
            <ul class="sidebar-methods">
              <#list repo.methods as method>
                <li><a href="#${method.id}" class="sidebar-method-link">${method.methodName?html}()</a></li>
              </#list>
            </ul>
          </#if>
        </li>
      </#list>
    </ul>
  </nav>

  <main id="content">

    <!-- ── Stats banner ── -->
    <div class="stats-banner">
      <div class="stat-card">
        <div class="stat-num">${repoCount}</div>
        <div class="stat-label">Repositories</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">${methodCount}</div>
        <div class="stat-label">Repository Methods</div>
      </div>
      <div class="stat-card ${missingTxClass}">
        <div class="stat-num">${missingTx}</div>
        <div class="stat-label">Methods without @Tx path</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">${totalIndexedMethods}</div>
        <div class="stat-label">Total Indexed Methods</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">${classCount}</div>
        <div class="stat-label">Classes Scanned</div>
      </div>
      <div class="stat-card ${depthLimitClass}" title="Tree cut at depth limit (${maxDepth}). Raise MAX_DEPTH in TransactionalAnalyzer.java if needed.">
        <div class="stat-num">${depthLimitHits}</div>
        <div class="stat-label">Depth limit reached (max=${maxDepth})</div>
      </div>
      <div class="stat-card ${cycleClass}" title="Number of cyclic call references detected across all caller trees.">
        <div class="stat-num">${cycleCount}</div>
        <div class="stat-label">Cyclic references</div>
      </div>
    </div>

    <!-- ── Legend + filters ── -->
    <div class="legend">
      <div class="legend-group">
        <div class="legend-group-title">
          Method node annotations
          <button class="group-btn" onclick="selectGroup('ann',true)">All</button>
          <button class="group-btn" onclick="selectGroup('ann',false)">None</button>
        </div>
        <div class="legend-items">
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="ann" data-value="tx-spring"><span class="badge tx-spring">@Transactional (Spring)</span></label>
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="ann" data-value="tx-jakarta"><span class="badge tx-jakarta">@Transactional (Jakarta)</span></label>
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="ann" data-value="tx-indirect"><span class="badge tx-indirect">@Tx (indirect)</span></label>
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="ann" data-value="no-tx"><span class="badge no-tx">No @Transactional</span></label>
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="ann" data-value="external"><span class="badge external">External / no source</span></label>
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="ann" data-value="cyclic"><span class="badge cyclic">↻ Cycle</span></label>
        </div>
      </div>
      <div class="legend-divider"></div>
      <div class="legend-group">
        <div class="legend-group-title">
          ① @Transactional coverage
          <button class="group-btn" onclick="selectGroup('tx',true)">All</button>
          <button class="group-btn" onclick="selectGroup('tx',false)">None</button>
        </div>
        <div class="legend-items">
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="tx" data-value="green"><span class="tx-dot tx-dot-green"></span> All call paths pass through @Transactional</label>
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="tx" data-value="red"><span class="tx-dot tx-dot-red"></span> One or more paths have no @Transactional</label>
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="tx" data-value="grey"><span class="tx-dot tx-dot-grey"></span> Never called in indexed source</label>
        </div>
      </div>
      <div class="legend-divider"></div>
      <div class="legend-group">
        <div class="legend-group-title">
          ② Endpoint origin
          <button class="group-btn" onclick="selectGroup('ep',true)">All</button>
          <button class="group-btn" onclick="selectGroup('ep',false)">None</button>
        </div>
        <div class="legend-items">
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="ep" data-value="blue"><span class="tx-dot ep-dot-blue"></span> All call paths originate from an @Endpoint</label>
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="ep" data-value="violet"><span class="tx-dot ep-dot-violet"></span> One or more paths come from a non-@Endpoint (scheduler, CLI, …)</label>
          <label class="legend-dot-item legend-filter-item"><input type="checkbox" checked data-filter="ep" data-value="grey"><span class="tx-dot tx-dot-grey"></span> Never called in indexed source</label>
        </div>
      </div>
      <#if allOverrideTypes?has_content>
      <div class="legend-divider"></div>
      <div class="legend-group" id="overrides-dropdown">
        <div class="legend-group-title">
          ③ Implemented / Overridden types
          <button class="group-btn" onclick="selectAllOverrides()">All</button>
          <button class="group-btn" onclick="selectNoneOverrides()">None</button>
        </div>
        <button class="od-toggle" id="od-toggle" onclick="toggleOverridesPanel(event)">
          Select types…
          <span class="od-count" id="od-count">${allOverrideTypes?size} / ${allOverrideTypes?size}</span>
          <span class="od-chevron" id="od-chevron">▾</span>
        </button>
        <div class="od-panel" id="od-panel" style="display:none">
          <div class="od-toolbar">
            <input type="text" class="od-search" id="od-search" placeholder="Search type…" oninput="filterOverridesSearch()">
          </div>
          <div class="od-list" id="od-list">
            <#list allOverrideTypes as fqn>
              <label class="od-item">
                <input type="checkbox" checked data-filter="override" data-value="${fqn?html}">
                <span class="od-fqn" title="${fqn?html}">${fqn?html}</span>
              </label>
            </#list>
          </div>
        </div>
      </div>
      </#if>
      <div class="legend-actions">
        <label class="in-tree-toggle">
          <input type="checkbox" id="in-tree-filter" onchange="applyFilters()">
          Filter within trees
        </label>
        <div class="global-actions">
          <button class="global-btn" onclick="selectAllFilters()">All</button>
          <button class="global-btn" onclick="selectNoneFilters()">None</button>
        </div>
      </div>
    </div>

    <!-- ── Global tree toolbar ── -->
    <div class="global-tree-toolbar">
      <button class="global-btn" onclick="expandAllGlobal()">Expand all</button>
      <button class="global-btn" onclick="collapseAllGlobal()">Collapse all</button>
      <button class="global-btn" id="paths-view-btn" onclick="togglePathsView()">Paths view</button>
    </div>

    <!-- ── Repository sections ── -->
    <#list repos as repo>
    <section id="${repo.id}" class="repo-section">
      <h2 class="repo-title">
        <span class="tx-dot repo-header-dot ${repo.txCssClass}" title="${repo.txTooltip?html}"></span>
        <span class="tx-dot repo-header-dot ${repo.epCssClass}" title="${repo.epTooltip?html}"></span>
        <span class="repo-icon">📦</span> ${repo.simpleName?html}
        <span class="file-ref">${repo.filePath?html}</span>
      </h2>
      <div class="toolbar">
        <button onclick="expandAll('${repo.id}')">Expand all</button>
        <button onclick="collapseAll('${repo.id}')">Collapse all</button>
      </div>
      <#if repo.methods?has_content>
        <#list repo.methods as method>
          <details class="method-tree" id="${method.id}"
              data-tx="${method.txData}"
              data-ep="${method.epData}"
              data-ann="${method.annData?html}"
              data-never-called="${method.neverCalled?string}"
              data-overrides="${method.overridesData?html}">
            <summary class="method-tree-header">
              <span class="tx-dot ${method.txCssClass}" title="${method.txTooltip?html}"></span>
              <span class="tx-dot ${method.epCssClass}" title="${method.epTooltip?html}"></span>
              <button class="expand-btn" onclick="expandMethodTree(event,'${method.id}')" title="Expand all">⊞</button>
              <span class="method-sig">${method.methodName?html}${method.paramSummary?html}</span>
              <#if method.rootOverridesFrom?has_content>
                <#assign rootOverrideDisplay = method.rootOverridesFrom?replace(",", ", ")>
                <span class="badge overrides-from" title="Implements / overrides from ${rootOverrideDisplay?html}">◁ ${rootOverrideDisplay?html}</span>
              </#if>
              <#list method.headerBadges as b>
                <span class="badge ${b.cssClass}">${b.text}</span>
              </#list>
            </summary>
            <div class="method-tree-body">
              <#if method.callers?has_content>
                <#list method.callers as caller>
                  <@node caller/>
                </#list>
              <#else>
                <p class="empty-note">No callers found in indexed source.</p>
              </#if>
            </div>
          </details>
        </#list>
      <#else>
        <p class="empty-note">No methods found.</p>
      </#if>
    </section>
    </#list>

  </main>

  <script>
${js}
  </script>
</body>
</html>
