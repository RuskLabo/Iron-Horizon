package com.lunar_prototype.iron_horizon.client.ui;

import java.util.ArrayList;
import java.util.List;

public abstract class UiContainer extends UiElement {
    protected List<UiElement> children = new ArrayList<>();

    public void addChild(UiElement child) {
        children.add(child);
    }

    public List<UiElement> getChildren() {
        return children;
    }

    @Override
    public void render() {
        if (!visible) return;
        for (UiElement child : children) {
            child.render();
        }
    }
}
