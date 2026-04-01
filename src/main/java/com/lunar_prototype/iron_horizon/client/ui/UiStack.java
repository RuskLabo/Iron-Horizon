package com.lunar_prototype.iron_horizon.client.ui;

public class UiStack extends UiContainer {
    public enum Orientation {
        VERTICAL,
        HORIZONTAL
    }

    public Orientation orientation = Orientation.VERTICAL;
    public float spacing = 5;

    public UiStack(Orientation orientation) {
        this.orientation = orientation;
    }

    @Override
    public float getPreferredWidth() {
        float w = 0;
        if (orientation == Orientation.HORIZONTAL) {
            for (UiElement child : children) {
                if (!child.visible) continue;
                w += child.getPreferredWidth() + child.marginLeft + child.marginRight;
            }
            if (!children.isEmpty()) w += spacing * (children.size() - 1);
        } else {
            for (UiElement child : children) {
                if (!child.visible) continue;
                w = Math.max(w, child.getPreferredWidth() + child.marginLeft + child.marginRight);
            }
        }
        return w + paddingLeft + paddingRight;
    }

    @Override
    public float getPreferredHeight() {
        float h = 0;
        if (orientation == Orientation.VERTICAL) {
            for (UiElement child : children) {
                if (!child.visible) continue;
                h += child.getPreferredHeight() + child.marginTop + child.marginBottom;
            }
            if (!children.isEmpty()) h += spacing * (children.size() - 1);
        } else {
            for (UiElement child : children) {
                if (!child.visible) continue;
                h = Math.max(h, child.getPreferredHeight() + child.marginTop + child.marginBottom);
            }
        }
        return h + paddingTop + paddingBottom;
    }

    @Override
    public void updateLayout() {
        float currentX = x + paddingLeft;
        float currentY = y + paddingTop;

        for (UiElement child : children) {
            if (!child.visible) continue;
            child.x = currentX + child.marginLeft;
            child.y = currentY + child.marginTop;
            child.width = child.getPreferredWidth();
            child.height = child.getPreferredHeight();
            
            if (orientation == Orientation.HORIZONTAL) {
                currentX += child.width + child.marginLeft + child.marginRight + spacing;
            } else {
                currentY += child.height + child.marginTop + child.marginBottom + spacing;
            }
            child.updateLayout();
        }
    }
}
