package dev.pixelied.survival.execution;

import java.util.Objects;
import java.util.Optional;

public sealed interface ExecutionStatus {
    record WaitingForServer(String reason, Optional<ExecutionCommand> command) implements ExecutionStatus {
        public WaitingForServer {
            reason = Objects.requireNonNull(reason, "reason");
            command = Objects.requireNonNull(command, "command");
        }

        public WaitingForServer(String reason) {
            this(reason, Optional.empty());
        }

        public WaitingForServer(String reason, ExecutionCommand command) {
            this(reason, Optional.of(Objects.requireNonNull(command, "command")));
        }
    }

    record Confirmed(String detail) implements ExecutionStatus {
        public Confirmed {
            detail = Objects.requireNonNull(detail, "detail");
        }
    }

    record Failed(String reason, boolean replanRequired) implements ExecutionStatus {
        public Failed {
            reason = Objects.requireNonNull(reason, "reason");
        }
    }
}
