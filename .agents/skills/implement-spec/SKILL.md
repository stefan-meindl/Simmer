---
name: implement-spec
description: "Implement the result of /to-spec and /to-tickets in code."
disable-model-invocation: true
---

You have been provided a spec. This spec should have tickets associated with it, describing how to implement the spec.

The issue tracker should have been provided to you. If not, tell the user to run `/setup-matt-pocock-skills`.

The goal is the entire spec implemented on a single **integration branch**, with every ticket resolved the way the issue tracker closes work.

The tickets are not a list of steps. They are a **task graph** with blocking relationships between them. This means there is always a **frontier** of tickets which are ready to be grabbed.

Communication to and from subagents should be sparse. Communicate primarily through **context pointers**: to the spec, tickets, research notes, and previous commits. Don't duplicate information already available via pointers.

**Implementer subagents** should be run in the background where possible for maximum concurrency.

## Steps

1. Read the spec and tickets to understand the task graph. A spec that is itself one ticket with no sub-tickets is a **leaf**: skip the merger subagent, have the implementer open the PR from its own branch, and run steps 7-9 on that branch.

2. (optional) Use an **exploration subagent** to conduct any exploration required by the tickets - relevant codebase files or external documentation. Ensure the exploration subagent can save files - it should save its markdown notes in a directory outside the repo, accessible by all future subagents. This lets **implementer subagents** focus on implementation rather than exploration.

3. Fetch, then create the integration branch from the up-to-date default branch, not the current HEAD (it may be a merged feature branch). If the issue tracker closes work through PRs, or the user asks for one, open a draft PR after the first merge in step 5 (a branch with no commits ahead of main can't open one), marked as closing the spec and tickets.

4. Use **implementer subagents** to implement each ticket, each in its own worktree on its own branch. Each implementer subagent:
   - confirms its worktree is based on the integration branch before starting, and resets onto it if not;
   - calls the Skill tool with `tdd` to build the ticket. No user is reachable, so it picks the seams itself and lists them in its report, together with the red run output for each behaviour (or says the red step was skipped);
   - merges the integration branch tip into its own branch before reporting done

5. Once an **implementer subagent** completes, merge its work to the integration branch with a **merger subagent**.

6. If this changes the **frontier** of available tickets, kick off more **implementer subagents** to work on the new tickets. This allows for maximum concurrency.

7. Once all tickets are complete, call the Skill tool with `code-review` on the integration branch. Fix all issues raised by the code review in a single **implementer subagent**.

8. If a draft PR exists, mark it ready for review, then confirm CI is green (`gh pr checks`) or report each failure. Otherwise, resolve each ticket the way the issue tracker closes work, and report the integration branch.

9. Clean up all **implementer subagent** worktrees. Keep the throwaway branch and its worktree.

## Testing instructions

Every PR this skill opens (draft or leaf) must include a **How to test** section: concrete steps a human can follow on a device, per acceptance criterion that needs eyes on it. Follow `docs/manual-testing.md` for how to run the app, and add or update that doc's checklist for the feature.

If some of the feature cannot be exercised on the feature branch because the UI that reaches it is missing (e.g. a screen whose entry point lands in a later ticket), create a **throwaway branch** (e.g. `throwaway/issue-<n>-manual-test`) off the feature or integration branch that adds the minimal temporary wiring (entry buttons, stub routes, seed data) so the user can test everything end to end. Never merge it and never open a PR from it. Push it, and in the PR body and final report give its name and what it fakes. Clean it up only when the user says so.
