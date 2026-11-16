package io.github.qdiaps.jsaver.contract;

public sealed interface Payload {
    record Text(String text) implements Payload {}
}
