package com.lunar_prototype.iron_horizon.client.ui;

public abstract class UiElement {
    public float x, y;
    public float width, height;
    public float paddingLeft, paddingTop, paddingRight, paddingBottom;
    public float marginLeft, marginTop, marginRight, marginBottom;
    public boolean visible = true;

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
