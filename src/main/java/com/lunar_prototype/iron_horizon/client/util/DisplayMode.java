package com.lunar_prototype.iron_horizon.client.util;

public enum DisplayMode {
    WINDOWED("Windowed"),
    BORDERLESS("Borderless"),
    FULLSCREEN("Fullscreen");

    private final String label;

    DisplayMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public DisplayMode next() {
        return switch (this) {
            case WINDOWED -> BORDERLESS;
            case BORDERLESS -> FULLSCREEN;
            case FULLSCREEN -> WINDOWED;
        };
    }
}
