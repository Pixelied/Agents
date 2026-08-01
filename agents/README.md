# Registered agents

Each active agent instance gets a unique lowercase directory created by:

```bash
python agentctl.py register --id <unique-id> --provider <provider> --model <model>
```

Do not manually copy another agent's directory. The instance owns its `notes/` directory, while `inbox/` receives structured handoffs. Durable task knowledge belongs in task events or handoffs, not private notes.
