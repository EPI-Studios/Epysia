package fr.epistudio.epysia;

public enum CpuTimings {
    POLL,
    UPDATE,
    RENDER,
    COLLECT,
    DRAIN_SUBMIT,
    SWAP_BUFFERS;

    public String label() {
        return switch (this) {
            case POLL -> "poll";
            case UPDATE -> "update";
            case RENDER -> "render";
            case COLLECT -> "collect";
            case DRAIN_SUBMIT -> "drain-submit";
            case SWAP_BUFFERS -> "swap-buffers";
        };
    }
}
