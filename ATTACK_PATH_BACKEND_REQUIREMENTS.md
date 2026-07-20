# Attack Path (#6647) — Besoins backend pour brancher de vraies données

> À transmettre à Laurent. Objectif : lancer un **vrai scénario** et voir des résultats
> réels dans l'onglet **Chemin d'attaque** (aujourd'hui plusieurs zones sont mockées
> ou vides côté front faute de données exposées par le back).
>
> Le front est déjà codé pour **consommer** ces champs dès qu'ils existent (structure
> prête, valeurs vides/masquées tant qu'absentes). Rien à refaire côté front après.

## Contexte

Le front lit surtout deux DTO :
- `AttackPathNodeDTO` (nœuds injecteur / endpoint / finding-type / finding).
- `AttackPathExecutionDetailDTO` (détail d'une exécution, ouvert dans le drawer « Result & Terminal »).

Les exécutions seedées (`AttackPathSeedService`) utilisent des identifiants **synthétiques**
(`contract-<injector>`, `step-tpl-<injector>`, `payloadName = "<injector>-payload"`) qui ne
correspondent à **aucun** InjectorContract / Payload / Inject réel → toute résolution live
(techniques ATT&CK, detection remediations, lien inject) renvoie vide sur le seed. Sur un
**vrai scénario**, ces liens existent : il suffit que le back les **expose** sur les DTO.

## Ce qu'on attend, par priorité

### P0 — débloque les features déjà construites côté front

1. **`injectId` (et idéalement `payloadId`) sur `AttackPathExecutionDetailDTO`**
   - Usage front : bouton **« Action details »** dans le drawer → redirige vers l'inject
     (`/…/simulations/{simId}/injects/{injectId}`). Aujourd'hui masqué faute d'id.
   - Front prêt : `onOpenInject` déjà câblé (consomme `detail.injectId`).

2. **Detection remediations sur le détail d'exécution**
   - Champ souhaité : `detectionRemediations: DetectionRemediationOutput[]` sur
     `AttackPathExecutionDetailDTO` (ou exposer `payloadId` pour que le front aille les
     chercher via l'API payload existante). Source = les detection remediations du **payload**
     de l'inject (celles de l'onglet « Remediation » d'un inject).
   - Usage front : onglet **« Remediation »** dédié dans le drawer (déjà en place), qui
     affiche par **security platform** la règle de détection. Aujourd'hui « No detection
     remediation available ».
   - Shape par entrée (réutiliser l'existant `DetectionRemediationOutput`) :
     `detection_remediation_collector` (type collecteur), `detection_remediation_values` (HTML/règle),
     `detection_remediation_author_rule`.
   - Note : feature EE (comme l'onglet inject) — garder le gating côté back.

3. **Techniques MITRE ATT&CK sur les nœuds injecteur / exécution**
   - Champ souhaité : `attackPatterns: AttackPatternSimple[]` (au minimum `attack_pattern_external_id`
     + `attack_pattern_name`) sur `AttackPathNodeDTO` (injecteur) et/ou `AttackPathExecutionDetailDTO`.
   - Résolution : `AttackPathExecution.contractExternalId` → `InjectorContract` →
     `injectorContract.getAttackPatterns()` → `externalId` (exactement ce que fait déjà
     `PayloadMapper` pour `payload_attack_patterns`). Batch-résoudre les contract ids distincts
     pour garder l'invariant « 2 lectures ».
   - Usage front : chips `[T1046] …` dans le drawer. Aujourd'hui **table statique front**
     (`attack-path-mitre.ts`) pour les 5 injecteurs du seed → à **remplacer** par le champ back.

4. **Verdicts prévention / détection / vulnérabilité par finding**
   - Champ souhaité : les verdicts réels par finding (prevention/detection/vulnerability →
     success | failed | unknown), tirés des expectations de l'exécution productrice.
   - Usage front : `FindingDetailPanel` (icônes de verdict en haut). Aujourd'hui **placeholder
     statique** (`findingExpectations`, `TODO(#6647)`).

5. **Vraies security platforms + alertes sur le détail d'exécution**
   - Champ souhaité : par exécution, les résultats d'expectation **par plateforme** :
     `{ platformType, platformName, status (prevented/detected/…), detectedAt, alerts: [{ id, title, date }] }`.
   - Usage front : « Prevented by / Detected by » + popover d'alertes + CTA « N alerts ».
     Aujourd'hui **mocké** (CrowdStrike/Splunk en dur + `buildAlerts` = 2 titres synthétiques).
   - ⚠️ Règle métier à confirmer : **un « prevented » implique « detected »** (on ne bloque pas
     ce qu'on ne voit pas). Le front applique déjà cette implication ; à valider/garantir côté back.

### P1 — qualité des données / cohérence

6. **`injectorType` / slug sur le nœud injecteur** (`AttackPathNodeDTO`)
   - Aujourd'hui le front devine le slug d'icône depuis le label (`crackmapexec → netexec`,
     `TODO(#6647)` dans `InjectorNode.tsx`). Exposer le type réel supprime la devinette.

7. **Type de finding natif `file`** (« Captured Files »)
   - Aujourd'hui mappé temporairement sur `share`. Prévoir un vrai type quand l'exfil est modélisée.

8. **Seed réaliste (optionnel, pour démo sans vrai scénario)**
   - Faire pointer `AttackPathSeedService` sur de vrais `contractExternalId` / `payloadId` /
     `injectId` pour que le seed résolve techniques + remediations + lien inject. Sinon, tester
     directement avec un **vrai scénario exécuté** (ce qui est l'objectif).

### P2 — scalabilité (plus tard)

9. **Score de chokepoint côté back** (`chokepointScore` sur `AttackPathNodeDTO` endpoint)
   - v1 est calculée **côté front** (somme des `findingCounts` = endpoint le plus exposé, faute de
     criticité d'asset). Pour l'échelle + une vraie pondération (betweenness + criticité d'asset via
     tags/tier), exposer un score serveur, triable/paginable.
   - Nécessite au préalable une notion de **criticité d'asset** (tags / équivalent Domain-Admin),
     inexistante aujourd'hui.

### P1/P2 — chaîne d'attaque intra-endpoint (kill-chain / séquence d'étapes)

**Besoin métier** : modéliser une **séquence causale** d'étapes, p.ex. sur un même endpoint :
`port 445 ouvert` → `SMB validé sur 445` → `NULL session enum réussie` → `énumération des shares`
(et, entre hôtes, le pivot `host compromis → host suivant`).

**Ce qui existe déjà (donc partiellement faisable front-only, voir plus bas)** :
- `AttackPathExecution` porte `executedAt`, `stepTemplateId`/`stepId`, `payloadName`,
  `prevention/detection status` — **exposés au front** (`AttackPathNodeDTO.executedAt`,
  `.stepTemplateId`). → l'**ordre chronologique** et le **regroupement par étape** sont dérivables.
- `AttackPathExecutionFinding` relie **exécution ↔ finding produit** → on sait quel step a produit
  le port 445 / les shares.
- La source d'une exécution peut être un **ASSET** (pas seulement un injecteur) → les **pivots /
  latéralisation** (host→host) sont déjà modélisés côté données.
- Types de finding déjà présents : `port`, `share`, `credentials`, `username`, `cve`…

**Ce qui manque côté back pour rendre une vraie kill-chain** :

10. **Dépendance causale entre étapes** (et pas seulement l'ordre chronologique).
    - Aujourd'hui les exécutions sont des arêtes `source → endpoint` indépendantes ; rien ne dit
      « le port 445 a *permis* le check SMB qui a *permis* le NULL enum qui a *permis* l'énum shares ».
    - Souhaité : exposer le **DAG d'étapes du scénario** (le chaining engine a déjà steps + conditions
      + dépendances) corrélé aux exécutions via `stepTemplateId` → une liste ordonnée
      `stepTemplateId`, `dependsOn: string[]` (prérequis) par exécution. À défaut, un lien explicite
      **finding/port consommé → exécution suivante** (`consumedFindingIds` sur l'exécution).

11. **États / résultats intermédiaires en nœuds de première classe**.
    - « SMB confirmé sur 445 » : aujourd'hui non modélisé. Soit enrichir le finding `port`
      (`service`/`protocol` = smb), soit un type `service`.
    - « NULL session réussie » : c'est un **résultat de technique** (attack-pattern / expectation),
      pas un finding. Souhaité : exposer par exécution les **techniques + verdict** (`attackPatterns[]`
      + succès/échec) pour matérialiser l'étape-technique et son issue (cf. point 3 P0).

12. **Corrélation step ↔ définition** : exposer, pour chaque `stepTemplateId`, le **label** et
    l'**ordre/priorité** de l'étape (nom lisible « NULL session enum », rang dans le scénario), pour
    étiqueter la timeline sans deviner depuis `payloadName`.

> Sans 10–12, on peut afficher une **séquence chronologique** d'étapes par endpoint (ordre = temps),
> mais pas une **causalité** garantie ni les états intermédiaires non exécutés comme étapes propres.

## Récap « 1 ligne par champ » (pour Laurent)

Sur **`AttackPathExecutionDetailDTO`** : `injectId`, `payloadId`, `attackPatterns[]`,
`detectionRemediations[]`, `securityPlatforms[] { type, name, status, detectedAt, alerts[] }`.
Sur **`AttackPathNodeDTO`** (injecteur) : `attackPatterns[]`, `injectorType`.
Sur **le finding** : verdicts prevention/detection/vulnerability réels.
Sémantique : garantir **prevented ⇒ detected**.
Pour la **kill-chain** : par exécution `stepTemplateId` (+ label/rang de l'étape) et `dependsOn[]`
(ou `consumedFindingIds[]`) ; enrichir le finding `port` d'un `service`/`protocol` ; techniques +
verdict par exécution pour matérialiser les étapes-techniques.

Une fois ces champs exposés, le front bascule automatiquement du mock/statique/vide vers les
vraies données (les points 1-5 s'allument sans dev front supplémentaire, sauf retrait de la
table statique MITRE au profit du champ back).
