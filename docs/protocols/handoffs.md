# Handoffs

A handoff is a structured transfer, not “I did some stuff, good luck.”

Every handoff requires:

- source and destination agent IDs;
- a plain-language summary;
- completed items;
- remaining items;
- one exact next action;
- changed files;
- verification already run;
- known risks or blockers.

The handoff is written to the task's append-only `handoffs/` directory and copied into the destination agent's inbox. The receiving agent must still refresh repository state and inspect active leases before acting.

A handoff does not transfer a lease automatically. The sender releases the scope, and the receiver claims it after the release becomes authoritative.
