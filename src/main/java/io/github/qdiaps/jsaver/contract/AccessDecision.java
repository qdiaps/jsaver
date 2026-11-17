package io.github.qdiaps.jsaver.contract;

import java.util.Objects;

public sealed interface AccessDecision {
    record Allowed() implements AccessDecision {}
    record Denied(DenialReason reason, boolean shouldNotify) implements AccessDecision {
        public Denied {
            Objects.requireNonNull(reason);
        }
    }
}
