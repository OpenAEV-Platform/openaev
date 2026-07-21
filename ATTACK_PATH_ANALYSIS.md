# Attack Path (#6647) — Implémentation POC + Analyse compétitive & Plan d'action

> Document de travail (local, non commité). Base pour la future spec.
> Captures live : `ap-01-overview.png` … `ap-06-expanded.png` (racine du repo).

---

## Partie A — Ce qui a été livré dans cette session

Décisions actées : **Q1 = A** (clic finding → vue focus + panel), **Q2 = A** (seed image collecteur CrowdStrike dans minio).

| Tâche | Statut | Détail |
|---|---|---|
| **T1 — Clic nœud final → panel de détails** | ✅ Codé + validé live | En vue clusterisée, cliquer un nœud finding bascule en vue focus **et** ouvre `FindingDetailPanel` (comme un clic d'item du drawer). Généralisé à n'importe quel finding : les nœuds portent désormais `assetNodeId` (helpers), transmis via `AttackPathFlow`, résolu dans `SimulationAttackPath.openFindingFromGraph` → focus endpoint + panel + résolution des actions productrices. Fallback vers l'ancien highlight si l'endpoint n'est pas résoluble. Capture `ap-02`. |
| **T2 — Icône CrowdStrike manquante** | ✅ Résolu (seed minio, éphémère) | Cause racine : aucun collecteur CrowdStrike installé en dev → `/api/collectors/openaev_crowdstrike/image` renvoyait 404 → fallback bouclier. Le front était déjà correct (endpoint collecteur + fallback, jamais lié à l'exécuteur). Seed du vrai logo dans minio : `mc cp .../connectors/logos/openaev_crowdstrike-logo.png .../collectors/images/openaev_crowdstrike.png` (tenant `2cffad3a…`). Endpoint → 200. Captures `ap-04`/`ap-06`. |
| **T3 — Nb d'alertes + CTA bleu par plateforme** | ✅ Codé + validé live | `ExecutionResultTerminalPanel.SecurityPlatformItem` affiche « N alerts » en lien bleu (souligné, `primary.main`) ; ouvre le popover d'alertes existant. Captures `ap-04`/`ap-05`. |

**Validation** : `check-ts` ✅, `eslint` (attack_path) ✅, `vitest run attack` ✅ **24/24**. Fins de ligne normalisées CRLF→LF.

**⚠️ À savoir** : le seed CrowdStrike (T2) est **éphémère** — stocké dans le volume minio du tenant dev. Si le volume est réinitialisé, relancer le `mc cp`. En prod, l'icône réelle s'affichera dès qu'un collecteur CrowdStrike est installé (aucun code à changer).

**Fichiers modifiés** :
- `attack-path-flow-helpers.ts` — champ `assetNodeId` sur les nœuds finding (+ param `endpointNodeId` de `pushFindingColumn`).
- `AttackPathFlow.tsx` — propage `assetNodeId` dans `onFindingSelect`.
- `SimulationAttackPath.tsx` — `openFindingFromGraph` + branchement dans `onFindingSelect`.
- `ExecutionResultTerminalPanel.tsx` — CTA « N alerts ».

---

## Partie A-bis — Livré via loop (2026-07-16, itérations autonomes)

Toutes ces briques sont **codées, validées** (check-ts, eslint attack_path, vitest 24/24) **et vérifiées au navigateur** (captures `ap-07`…`ap-16`).

| Brique | Détail | Statut |
|---|---|---|
| **Dédup endpoints** | `buildClusteredAttackPathFlow` réécrit : les injecteurs convergent sur **un hub d'endpoints partagé** (dédup), puis finding clusters globaux. Vue `actions > endpoints > findings` au lieu de `action > endpoint > finding` répété par injecteur. Expansion → endpoints distincts, triés par exposition décroissante. | ✅ |
| **Clics uniformisés** | finding = highlight amont + panel ; injecteur = highlight aval + drawer ; clusters = expand/collapse ; mutuellement exclusifs. | ✅ |
| **MITRE ATT&CK** | `attack-path-mitre.ts` (map statique injecteur→technique, POC). TTPs **retirés des nœuds**, affichés **uniquement dans le drawer** (`AttackPatternChip` dans le panel Result). GA = champ back sur `AttackPathNodeDTO` (résoudre `contractExternalId`→`InjectorContract.attackPatterns`). | ✅ |
| **Remédiation CVE** | `FindingDetailPanel` réutilise `vulnerability_remediation` (via `fetchVulnerabilityByExternalId`) en texte inerte, état vide gracieux. | ✅ |
| **Drawer inject (clic injecteur)** | clic injecteur → panel Result & Terminal d'une **exécution représentative** : commande passée (onglet Terminal), prévention/détection (onglet Result), TTPs (header). Symétrique du finding. | ✅ |
| **Chokepoints v1** | score endpoint = somme `findingCounts` (pas de criticité asset → « le plus de findings »). Carte **« Top chokepoints »** + popover liste rangée (clic → focus endpoint) ; **badge flamme + rang** sur les endpoints les plus exposés (révélés en tête à l'expansion du hub). 100 % front. | ✅ |

**Dépend du back (différé)** : détection-remédiation payload/collector (onglet inject) — l'exécution attack-path ne porte qu'un `payloadName` synthétique non résolvable ; il faut un champ back exposant le payload id / detection remediations sur le DTO d'exécution. Même logique que le GA du MITRE.

## Partie B — Analyse compétitive (base de spec)

### 1. Inventaire des capacités actuelles

**Réel (adossé aux données d'exécution)**
- Graphe clusterisé injecteur → cluster endpoint (+N) → cluster finding-type (icône+compte), construit en 2 lectures SQL, mode collapsed pour l'échelle.
- 4 cartes de synthèse (Endpoints / Credentials / Users / CVEs) cliquables → drawer paginé + recherche.
- Drill-down progressif par batch (10) avec nœud « +rest », endpoints et findings.
- Vue focus « chemin vers ce finding » (`buildFindingPathFlow`) restreinte aux injecteurs réellement producteurs (`producingInjectorIds`) + clusters contextuels.
- Focus bidirectionnel : finding→amont, injecteur→aval.
- Cross-focus feed d'exécutions (scroll + highlight de l'action productrice).
- Panel Result & Terminal : verdicts prevention/detection réels (GREEN/ORANGE/RED), cible, `command` + `terminalOutput` masqués côté serveur.
- Masquage secrets côté serveur ET front (credentials, sid, password_policy) — y compris dans la sortie terminal.
- Minimap (>40 nœuds), légende, panneaux latéraux, états focus accessibles (ARIA).

**Mocké — `TODO(#6647)` en attente back (Laurent)**
- Identité des plateformes sécu (CrowdStrike/Splunk) — inférée d'un booléen, hardcodée.
- Liste d'alertes du popover — `buildAlerts()` renvoie 2 titres synthétiques fixes.
- Verdicts prevention/detection/vulnerability **par finding** — objet statique.
- « Captured Files » = mapping temporaire sur le type `share` (pas de type `file` natif).
- i18n POC incomplète.

**Absent (ni code ni mock)**
- Score de risque / priorisation des chemins.
- Détection de **chokepoints** (nœud sur le plus de chemins).
- Mapping **MITRE ATT&CK**.
- Recommandations de remédiation.
- Trending inter-simulations / dans le temps.
- Export / reporting (PDF, CSV, lien partageable).
- Modélisation identité/AD (users→groupes→permissions, escalade de privilèges).
- Recherche/filtre libre au-delà des 5 catégories fixes.

### 2. Forces

| Force | Preuve | Pourquoi ça compte |
|---|---|---|
| Verdicts adossés à des preuves réelles (pas simulés) | `preventionStatus`/`detectionStatus` + snapshot d'exécution (command+output) | Différenciateur clé de Pentera/NodeZero/AttackIQ (« proof of exploitation »). L'ingrédient brut est là. |
| Clustering injecteur→endpoint→finding + drill-down progressif | `buildClusteredAttackPathFlow`, batch, 2 lectures SQL | Réponse saine (et efficiente) au problème de lisibilité que XM Cyber/Tenable/MS traitent par agrégation. |
| Focus bidirectionnel dans la même vue | `highlightGraphFinding` / `highlightGraphInjector` | Peu d'outils permettent de pivoter dans les deux sens sans renavigation. |
| Masquage secrets natif côté serveur (y c. sortie terminal) | `maskCredential`/`maskSecrets` | Vraie lacune ailleurs (creds en clair dans les logs). |
| Result & Terminal ancré spatialement au nœud (pas une modale) | `ExecutionResultTerminalPanel` en panneau in-flow | Meilleure architecture d'info que l'onglet rapport séparé de Pentera/NodeZero. |
| Construction de graphe déterministe et peu coûteuse | 2 lectures SQL indépendantes de la taille | Scalabilité by-design, atout durable. |

### 3. Écarts vs marché (priorisés)

**P0 — le « so what » manque aujourd'hui**
1. **Aucun chokepoint / priorisation.** XM Cyber, Tenable, MS Exposure Mgmt, BloodHound Enterprise mènent là-dessus (~2 % des expositions sont des chokepoints → ~99 % du scope de remédiation). Ici tous les chemins sont plats et égaux : impossible de savoir quel endpoint/credential, une fois corrigé, casse le plus de chemins. **Plus fort levier.**
2. **Pas de mapping MITRE ATT&CK.** Tenable/Cymulate/Picus/AttackIQ mappent chaque technique (heatmap + vocabulaire commun blue team). Nos injecteurs (nmap→T1046, hydra→T1110, impacket/crackmapexec→T1003…) l'impliquent déjà — gain peu coûteux.
3. **Pas de remédiation.** Tous les concurrents associent « voici le chemin » à « fais X ». Aggravé par les verdicts mockés « success » qui rassurent à tort sans action.

**P1 — parité visible en démo/bake-off**
4. **Données plateforme/alertes mockées** → risque de crédibilité en démo (2 mêmes titres d'alerte, plateforme = booléen).
5. **Lisibilité du graphe à l'échelle** : vue par défaut minuscule/dézoomée ; vue étendue = longue colonne fine nécessitant la minimap. Concurrents : vue chokepoint-first + recherche de chemins plutôt que « tout rendre puis zoomer ».
6. **Pas de blast-radius** (« si ce host est compromis, quoi d'autre devient atteignable »). MS Exposure Mgmt / Rapid7 l'ont en mode dédié.
7. **Pas de trending inter-simulations** (le picker swap les données, pas de diff). Pentera/NodeZero vendent la re-validation continue.
8. **Filtrage/recherche limités** (5 filtres fixes + recherche drawer sur 1 catégorie chargée).

**P2 — complétude / conformité**
9. **Pas d'export/reporting** (PDF/CSV) — table stakes reporting exécutif/conformité.
10. **Pas de modélisation identité/AD** — préciser que notre « attack path » = actions exécutées, ≠ atteignabilité de privilèges (catégorie BloodHound).
11. **« Captured Files » = hack sémantique** (`share`).
12. **Dette i18n.**

### 4. Plan d'action (squelette de spec)

**Now (P0) — rendre le graphe prescriptif**

| # | Titre | Approche | Effort | Dépend. | Critère de succès |
|---|---|---|---|---|---|
| N1 | **Scoring chokepoint** | Back : betweenness par nœud ASSET/finding sur les edges existants → `chokepointScore` sur le DTO. Front : tri/highlight top-N + carte « Top chokepoints » | L | — | Lister les 5 nœuds à corriger + % de chemins fermés par chacun |
| N2 | **Tag MITRE ATT&CK sur injecteurs/exécutions** | Table statique step-template→technique (cf. `STEP_TEMPLATE_CONTRACT_LABEL`) ; chip sur nœud injecteur + feed/Result | S | — (front-only possible) | Chaque injecteur montre ≥1 technique ATT&CK |
| N3 | **Remplacer plateforme/alertes mockées par le back** | Back (Laurent) : plateforme réelle + alertes (id/titre/date) sur `AttackPathExecutionDetailDTO` ; front : retirer `buildAlerts`/hardcodes | M + back | **Bloqué back** | Zéro chaîne plateforme/alerte hardcodée ; alertes tracées à un id réel |
| N4 | **Verdicts par finding réels** | Back : verdicts des exécutions productrices ; front : remplacer `findingExpectations` statique | S + back | **Bloqué back** | Verdicts dérivés des `producingActions` réels |

**Next (P1)**

| # | Titre | Approche | Effort | Critère |
|---|---|---|---|---|
| N5 | Remédiation par finding/chemin | Table statique type/technique→phrase de remédiation, dans `FindingDetailPanel` | S–M | ≥1 ligne de remédiation actionnable par finding |
| N6 | Lisibilité par défaut à l'échelle | Layout par sévérité/chokepoint, branches faibles repliées, vue « top paths » alternative | M–L | Sim 100+ endpoints lisible sans minimap |
| N7 | Vue blast-radius | Depuis un endpoint : atteignabilité aval (au-delà des edges exécutés) | L | Ensemble aval distinct de la « vue chemin pris » |
| N8 | Trending inter-simulations | Diff de 2 sims : delta compteurs + chokepoints fermés | M (dép. N1) | « 3 chokepoints de moins que le run précédent » |
| N9 | Filtrage enrichi | Barre : par injecteur, sévérité, OS/plateforme + recherche libre globale | M | Filtrer sans ouvrir de drawer |

**Later (P2)** : N10 Export/report (M) · N11 Type `file` natif (S–M, back) · N12 Décision périmètre identité/AD (L si retenu) · N13 i18n (S).

### 5. Questions ouvertes avant la spec
1. **Chokepoint** : back (scale) ou 1ʳᵉ version front sur le graphe collapsed déjà chargé ?
2. **Définition « asset critique »** (ancre du chokepoint/blast-radius) — inexistante aujourd'hui ; lier au tagging asset/asset-group existant ?
3. **Périmètre vs BloodHound** : rester exécution/finding-centric, ou modéliser l'atteignabilité théorique (escalade, AD) ? Data models différents.
4. **Timing back (N3/N4)** : V1 avec mocks étiquetés « preview/simulé », attendre le back, ou masquer l'UI plateforme jusqu'à données réelles ?
5. **Source de vérité ATT&CK** : lookup front (rapide) vs catalogue back (correct long terme, requis pour heatmaps de couverture) ?
6. **Plafond de rendu** : ReactFlow tient-il les plus grosses sims, ou N6 doit évaluer WebGL / vue liste « top paths » par défaut ?
7. **Format reporting** : livrable pentest audit-grade ou PDF/CSV léger suffisant ?

---

_Sources marché : XM Cyber, Cymulate, Tenable One APA, Microsoft Security Exposure Management, Rapid7 Exposure Command, SpecterOps BloodHound Enterprise, Picus, Pentera, Horizon3 NodeZero, AttackIQ, SafeBreach (URLs dans le rapport d'analyse)._
