package com.lunar_prototype.iron_horizon.client.ui;

import com.lunar_prototype.iron_horizon.client.render.FontRenderer;

public class UiLabel extends UiElement {
    public String text;
    public FontRenderer font;
    public float r = 1, g = 1, b = 1, a = 1;
    public float scale = 1.0f;

    public UiLabel(String text, FontRenderer font) {
        this.text = text;
        this.font = font;
    }

    public void setColor(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    @Override
    public void render() {
        if (!visible || text == null || text.isEmpty()) return;
        font.drawText(text, x, y, r, g, b, a, scale * uiScale);
    }

    @Override
    public void updateLayout() {
        width = getPreferredWidth();
        height = getPreferredHeight();
    }

    @Override
    public float getPreferredWidth() {
        if (text == null || text.isEmpty()) return 0;
        return font.getStringWidth(text) * scale * uiScale;
    }

    @Override
    public float getPreferredHeight() {
        return font.getFontSize() * scale * uiScale;
    }
}
