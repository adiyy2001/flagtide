# 0018 Admin editor state model

Status: accepted, 2026-10-04

## Context

A flag has one definition (description and variants) shared by every environment, and one configuration per environment (enabled, off variant, rules, default rule). The server stores them in one document with one revision, so any save by anyone moves the revision for everyone. The editor shows all of it on one page: a definition card and one tab per environment. An editor can have unsaved edits in the definition and in two environments at once, and a save in one tab must not throw away the edits in another.

Two more things shape the model. People type percentages and values as text, and half-typed text is not a valid number, so the form cannot hold parsed values only. And the server answers 409 when the revision sent in `If-Match` is stale, so the editor must say what to do next.

## Decision

- The editor state is one class, `FlagEditorState`, built from the loaded flag. It keeps a draft per section, where a section is `definition` or `environment:<key>`, and a serialized baseline per section.
- Drafts hold what the user typed: percent text, value text, and a stable `uid` per rule, condition, variant and rollout entry so lists keep identity while they are reordered.
- A section is dirty when its serialized draft differs from its baseline. The serialization leaves the `uid` fields out, so adding and removing a rule again is not a change.
- Validation is pure functions over drafts that return issues addressed by path (`rules.1.serve.rollout.0.percent`). Each field component shows the issues under its own path, and the save bar lists all of them. A section with issues cannot be saved. Rollout percentages are parsed to integer thousandths of a percent, so the sum check is exact.
- `accept(flag, savedSection?)` takes a flag returned by the server. The saved section and every clean section adopt the new server state. A dirty section keeps its draft and is measured against the new baseline, so it stays dirty only if it still differs from what the server has now.
- A save sends the revision the state last accepted. On 409 the editor fetches the latest flag and opens a dialog with a table of the paths where the server and the draft differ, and three choices: keep editing, load theirs (adopt the server version of that section and drop the draft) or save mine over theirs (accept the latest flag, then save the draft again with the new revision). The other sections are not touched by either choice.
- Leaving the page with a dirty state asks first, through a `canDeactivate` guard, and a `beforeunload` listener covers closing the tab.
- Lists and toggles outside the editor (the flag list, the segments page) use plain signals and the same 409 handling in a simpler form, because they change one value at a time.

## Alternatives

- Reactive forms: a form tree per environment with validators. The rules are nested lists with cross-field checks (sum of weights, references to variants and segments), which end up as custom validators over the whole value anyway, and the dirty tracking per section needs a second structure next to the form.
- Saving the whole flag at once: one Save button and one revision. It forces every edit through one conflict and loses the property that a change to prod does not touch the dev draft.
- Last write wins without `If-Match`: simpler, and it silently drops someone else's change, which an audit log then records as if it were intended.
- A store library (NgRx SignalStore): the state is small, local to one page and not shared, so the extra layer buys nothing here.

## Consequences

- The state class and the validators are plain TypeScript and are tested without the DOM. The component tests drive the whole page against a fake server that can answer 409.
- The rebase rule means a user can end up with a draft that equals the server state after a conflict and sees it become clean. That is intended.
- The baseline comparison runs on every edit. Flags are small, so the cost does not show.
- "Save mine over theirs" overwrites another person's change to the same section. The dialog shows the difference first, and the audit log keeps both versions.
