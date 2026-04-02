package com.lunar_prototype.iron_horizon.client.ui;

import com.lunar_prototype.iron_horizon.client.render.FontRenderer;
import static org.lwjgl.opengl.GL11.*;

public class UiButton extends UiElement {
    private final FontRenderer font;
    public String label;
    public boolean active;
    public boolean hovered;
    public float textScale = 1.45f;

    public float bgR = 0.18f, bgG = 0.2f, bgB = 0.18f, bgA = 1.0f;
    public float activeR = 0.16f, activeG = 0.58f, activeB = 0.26f;
    public float hoverR = 0.22f, hoverG = 0.28f, hoverB = 0.22f;
    public float borderR = 0.34f, borderG = 0.85f, borderB = 0.45f, borderA = 1.0f;

    public UiButton(FontRenderer font, String label) {
        this.font = font;
        this.label = label;
        this.widthMode = LayoutMode.FIXED;
        this.heightMode = LayoutMode.FIXED;
    }

    @Override
    public void render() {
        if (!visible) return;

        // Shadow
        glColor4f(0, 0, 0, 0.35f);
        glBegin(GL_QUADS);
        glVertex2f(x + 3 * uiScale, y + 4 * uiScale);
        glVertex2f(x + width + 3 * uiScale, y + 4 * uiScale);
        glVertex2f(x + width + 3 * uiScale, y + height + 4 * uiScale);
        glVertex2f(x + 3 * uiScale, y + height + 4 * uiScale);
        glEnd();

        // Background
        if (active)
            glColor4f(activeR, activeG, activeB, bgA);
        else if (hovered)
            glColor4f(hoverR, hoverG, hoverB, bgA);
        else
            glColor4f(bgR, bgG, bgB, bgA);
            
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Border
        glColor4f(borderR, borderG, borderB, borderA);
        glLineWidth(2 * uiScale);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // Text (Centered)
        float s = textScale * uiScale;
        float tw = font.getStringWidth(label) * s;
        float th = font.getFontSize() * s;
        float tx = x + (width - tw) / 2.0f;
        float ty = y + (height - th) / 2.0f + 1.0f * uiScale;
        
        font.drawText(label, tx, ty, borderR, borderG, borderB, 1.0f, s);
    }

    @Override
    public void updateLayout() {
        if (widthMode == LayoutMode.AUTO) {
            width = getPreferredWidth();
        }
        if (heightMode == LayoutMode.AUTO) {
            height = getPreferredHeight();
        }
    }

    @Override
    public float getPreferredWidth() {
        return (font.getStringWidth(label) * textScale + 40) * uiScale;
    }

    @Override
    public float getPreferredHeight() {
        return (font.getFontSize() * textScale + 20) * uiScale;
    }
}
