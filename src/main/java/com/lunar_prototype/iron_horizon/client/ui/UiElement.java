package com.lunar_prototype.iron_horizon.client.ui;

public abstract class UiElement {
    public enum LayoutMode {
        AUTO,  // 推奨サイズに合わせる
        FIXED  // 指定サイズを維持する
    }

    public enum HorizontalAlign {
        LEFT, CENTER, RIGHT
    }

    public enum VerticalAlign {
        TOP, MIDDLE, BOTTOM
    }

    public float x, y;
    public float width, height;
    public float paddingLeft, paddingTop, paddingRight, paddingBottom;
    public float marginLeft, marginTop, marginRight, marginBottom;
    public boolean visible = true;
    
    public LayoutMode widthMode = LayoutMode.AUTO;
    public LayoutMode heightMode = LayoutMode.AUTO;
    public HorizontalAlign horizontalAlign = HorizontalAlign.LEFT;
    public VerticalAlign verticalAlign = VerticalAlign.TOP;
    
    protected float uiScale = 1.0f;

    public void setUiScale(float scale) {
        this.uiScale = scale;
    }

    public abstract void render();

    public abstract void updateLayout();

    public abstract float getPreferredWidth();

    public abstract float getPreferredHeight();

    public void setPadding(float p) {
        paddingLeft = paddingTop = paddingRight = paddingBottom = p;
    }

    public void setMargin(float m) {
        marginLeft = marginTop = marginRight = marginBottom = m;
    }

    public boolean isMouseOver(float mx, float my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
