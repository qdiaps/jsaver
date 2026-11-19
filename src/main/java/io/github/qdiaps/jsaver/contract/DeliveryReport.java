package io.github.qdiaps.jsaver.contract;

import java.util.List;

public record DeliveryReport(MessageMetadata metadata, List<Item> items) {
    public boolean isAllDelivered() {
        return items.stream().allMatch(Item::delivered);
    }

    public record Item(Payload payload, List<String> fileIds, boolean delivered) {}
}
