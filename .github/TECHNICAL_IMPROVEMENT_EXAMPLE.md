# Filled example — simulation engine refactoring

This is the form filled with the hardest topic on the current list: real customer
impact, a performance risk we cannot yet measure, and no decision yet on who carries
the work. It is here to show what a good answer looks like when the data is missing.

Numbers and ticket references below are illustrative.

---

**Title**

```
refactor(execution): split simulation execution from the shared scheduler
```

**Labels:** `needs triage`, `technical improvement` · **Type:** Task

---

### What we propose to do

Split the simulation engine in two: a planner that builds the inject timeline, and a
runner that executes it. Today both sit in the same scheduler and share one thread
pool with the rest of the platform, so one large simulation slows everything else on
the instance.

Phase 1: put the current behaviour behind an interface. No visible change, no
behaviour change, so it can ship on its own.
Phase 2: swap in a runner with a bounded pool and a queue per simulation, behind a
feature flag, rolled out instance by instance.

On ownership: the rule says a performance risk with customer impact goes to a
vertical. No vertical owns the engine today, and OpenCTI is looking at the same
execution problem on their side. That is why the field below says "to be decided"
and not "vertical". This is the call we need out of the review.

### Effort estimate

more than a quarter

### Cost avoided

risk of incident

### Impact if we do nothing

Short term: nothing visible on small instances. Large runs are already slow, and the
two biggest customers split their campaigns by hand to stay under the limit. They
have not opened a ticket for it, they adapted.

Mid term: timeouts and partial runs during the quarterly campaigns, when volume is
highest. Support restarts runs by hand, and after a partial run we cannot say which
injects really executed, so the results of the simulation are not trustworthy.

Long term: the engine cannot carry the volume the roadmap plans, and every change on
the current design adds to what has to be undone. We end up rewriting it under
incident pressure, at the worst moment, and we miss the chance to align with the
OpenCTI execution work while both sides are still open.

### Evidence

What we have:

- 2 escalations in the last cycle, same customer, both on campaigns above ~400
  injects (JIRA-1042, JIRA-1078).
- One local measurement, single instance, nothing else running: median simulation
  time goes from 45 s at 50 injects to over 9 min at 500 injects. The curve bends
  somewhere between 200 and 400, we do not know where.

What we are missing, and this is the real gap:

- We do not know how many simulations run at the same time on a customer instance,
  nor the inject count per run. Today's telemetry is marketing telemetry and does not
  carry either.
- So we can say the engine degrades, we cannot say at which volume it breaks, and we
  cannot tell which customers are already close to the line.
- What we need before phase 2 is sized: inject count per run and concurrent
  simulations per instance, on the top 10 SaaS instances, over one full cycle. This
  measurement does not exist and nobody owns it yet. It is a few days of work on the
  telemetry side and it should be started now, in parallel, not after.

Links: Notion planning view, line "Simulation engine refactoring". The OpenCTI
overlap question is still open and has no owner.

### Area

simulation engine and execution, cross product (OpenCTI, XTM One)

### Staff driven or vertical

to be decided

### Security check

- [x] This is not a security finding. Security findings follow their own path and their own SLA.
