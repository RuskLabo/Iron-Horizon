package com.lunar_prototype.iron_horizon.client.render;

public enum FsrPreset {
    OFF(1.0f, "Off"),
    QUALITY(0.6666667f, "Quality"),
    BALANCED(0.58f, "Balanced"),
    PERFORMANCE(0.5f, "Performance"),
    ULTRA_PERFORMANCE(0.3333333f, "Ultra Performance");

    private final float renderScale;
    private final String label;

    FsrPreset(float renderScale, String label) {
        this.renderScale = renderScale;
        this.label = label;
    }

    public float renderScale() {
        return renderScale;
    }

    public String label() {
        return label;
    }

    public FsrPreset next() {
        return switch (this) {
            case OFF -> QUALITY;
            case QUALITY -> BALANCED;
            case BALANCED -> PERFORMANCE;
            case PERFORMANCE -> ULTRA_PERFORMANCE;
            case ULTRA_PERFORMANCE -> OFF;
        };
    }

    public FsrPreset previous() {
        return switch (this) {
            case OFF -> ULTRA_PERFORMANCE;
            case QUALITY -> OFF;
            case BALANCED -> QUALITY;
            case PERFORMANCE -> BALANCED;
            case ULTRA_PERFORMANCE -> PERFORMANCE;
        };
    }
}
