# Harden agent workspace after first production run

Fix the CI startup gate, add explicit agent lifecycle state management, and document artifact storage hygiene without changing completed task artifacts.

Machine-readable task metadata lives in `task.json`. Current state is derived from append-only events and active leases.
