package io.github.qdiaps.jsaver.contract;

public enum DenialReason {
    GLOBAL_MUTE("Бот временно на техническом обслуживании. Попробуйте позже."),
    CHAT_BANNED("Этот чат заблокирован."),
    USER_BANNED("Вам ограничен доступ к боту."),
    USER_MUTED("Вам временно ограничен доступ к боту."),
    NOT_IN_WHITELIST("Доступ к боту ограничен белым списком."),
    ADMIN_ONLY("Эта команда доступна только администраторам.");

    private final String text;

    private DenialReason(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
