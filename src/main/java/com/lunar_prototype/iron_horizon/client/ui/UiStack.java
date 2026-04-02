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
                w += child.getPreferredWidth() + (child.marginLeft + child.marginRight) * uiScale;
            }
            int visibleCount = 0;
            for (UiElement child : children) if (child.visible) visibleCount++;
            if (visibleCount > 1) w += spacing * uiScale * (visibleCount - 1);
        } else {
            for (UiElement child : children) {
                if (!child.visible) continue;
                w = Math.max(w, child.getPreferredWidth() + (child.marginLeft + child.marginRight) * uiScale);
            }
        }
        return w + (paddingLeft + paddingRight) * uiScale;
    }

    @Override
    public float getPreferredHeight() {
        float h = 0;
        if (orientation == Orientation.VERTICAL) {
            for (UiElement child : children) {
                if (!child.visible) continue;
                h += child.getPreferredHeight() + (child.marginTop + child.marginBottom) * uiScale;
            }
            int visibleCount = 0;
            for (UiElement child : children) if (child.visible) visibleCount++;
            if (visibleCount > 1) h += spacing * uiScale * (visibleCount - 1);
        } else {
            for (UiElement child : children) {
                if (!child.visible) continue;
                h = Math.max(h, child.getPreferredHeight() + (child.marginTop + child.marginBottom) * uiScale);
            }
        }
        return h + (paddingTop + paddingBottom) * uiScale;
    }

    @Override
    public void updateLayout() {
        // 子要素のスケールを同期
        for (UiElement child : children) {
            child.setUiScale(uiScale);
            child.updateLayout();
        }

        // 自身のサイズ決定 (AUTOモード時のみ)
        if (widthMode == LayoutMode.AUTO) {
            width = getPreferredWidth();
        }
        if (heightMode == LayoutMode.AUTO) {
            height = getPreferredHeight();
        }

        float currentX = x + paddingLeft * uiScale;
        float currentY = y + paddingTop * uiScale;

        float interiorW = width - (paddingLeft + paddingRight) * uiScale;
        float interiorH = height - (paddingTop + paddingBottom) * uiScale;

        for (UiElement child : children) {
            if (!child.visible) continue;
            
            if (orientation == Orientation.HORIZONTAL) {
                // 水平スタック: Xは累積、Yはアライメント
                child.x = currentX + child.marginLeft * uiScale;
                
                float childHWithMargin = child.height + (child.marginTop + child.marginBottom) * uiScale;
                if (child.verticalAlign == VerticalAlign.MIDDLE) {
                    child.y = y + (paddingTop + child.marginTop) * uiScale + (interiorH - childHWithMargin) / 2.0f;
                } else if (child.verticalAlign == VerticalAlign.BOTTOM) {
                    child.y = y + height - (paddingBottom + child.marginBottom) * uiScale - child.height;
                } else {
                    child.y = y + (paddingTop + child.marginTop) * uiScale;
                }
                
                currentX += child.width + (child.marginLeft + child.marginRight) * uiScale + spacing * uiScale;
            } else {
                // 垂直スタック: Yは累積、Xはアライメント
                child.y = currentY + child.marginTop * uiScale;
                
                float childWWithMargin = child.width + (child.marginLeft + child.marginRight) * uiScale;
                if (child.horizontalAlign == HorizontalAlign.CENTER) {
                    child.x = x + (paddingLeft + child.marginLeft) * uiScale + (interiorW - childWWithMargin) / 2.0f;
                } else if (child.horizontalAlign == HorizontalAlign.RIGHT) {
                    child.x = x + width - (paddingRight + child.marginRight) * uiScale - child.width;
                } else {
                    child.x = x + (paddingLeft + child.marginLeft) * uiScale;
                }
                
                currentY += child.height + (child.marginTop + child.marginBottom) * uiScale + spacing * uiScale;
            }
            
            child.updateLayout();
        }
    }
}
