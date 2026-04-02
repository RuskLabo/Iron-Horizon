package com.lunar_prototype.iron_horizon.client.ui;

import com.lunar_prototype.iron_horizon.client.GameRenderer;
import com.lunar_prototype.iron_horizon.client.render.FontRenderer;
import com.lunar_prototype.iron_horizon.client.render.Texture;

import static org.lwjgl.opengl.GL11.*;

public class UiActionCard extends UiElement {
    private final FontRenderer font;
    private final Texture icon;
    private final String label;
    private final String badgeText;
    private final int cost;
    public boolean active;
    public boolean hovered;

    public UiActionCard(FontRenderer font, Texture icon, String label, int cost, String badgeText) {
        this.font = font;
        this.icon = icon;
        this.label = label;
        this.cost = cost;
        this.badgeText = badgeText;
    }

    @Override
    public void render() {
        if (!visible) {
            return;
        }
        glColor4f(0, 0, 0, 0.32f);
        glBegin(GL_QUADS);
        glVertex2f(x + 4, y + 5);
        glVertex2f(x + width + 4, y + 5);
        glVertex2f(x + width + 4, y + height + 5);
        glVertex2f(x + 4, y + height + 5);
        glEnd();

        if (active) {
            glColor3f(0.10f, 0.40f, 0.18f);
        } else if (hovered) {
            glColor3f(0.22f, 0.28f, 0.22f);
        } else {
            glColor3f(0.14f, 0.18f, 0.14f);
        }
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        glColor4f(0.34f, 0.85f, 0.45f, 0.90f);
        glLineWidth(2.0f * uiScale);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        if (icon != null) {
            float iconSize = height - 16.0f;
            drawTexture(icon, x + 6.0f, y + 8.0f, iconSize, iconSize);
        }

        if (active) {
            glColor4f(0.10f, 0.42f, 0.18f, 0.88f);
        } else {
            glColor4f(0.06f, 0.08f, 0.06f, 0.85f);
        }
        glBegin(GL_QUADS);
        glVertex2f(x + 6.0f, y + height - 20.0f);
        glVertex2f(x + width - 6.0f, y + height - 20.0f);
        glVertex2f(x + width - 6.0f, y + height - 4.0f);
        glVertex2f(x + 6.0f, y + height - 4.0f);
        glEnd();

        glColor3f(0.93f, 0.98f, 0.93f);
        drawText(label, x + 56.0f, y + 32.0f, label.length() > 8 ? 0.92f : 1.05f);

        if (active) {
            glColor4f(0.10f, 0.42f, 0.18f, 0.88f);
        } else {
            glColor4f(0.06f, 0.08f, 0.06f, 0.85f);
        }
        glBegin(GL_QUADS);
        glVertex2f(x + width - 50.0f, y + 8.0f);
        glVertex2f(x + width - 8.0f, y + 8.0f);
        glVertex2f(x + width - 8.0f, y + 24.0f);
        glVertex2f(x + width - 50.0f, y + 24.0f);
        glEnd();
        glColor3f(0.90f, 0.95f, 0.90f);
        drawText(String.valueOf(cost), x + width - 40.0f, y + 20.0f, 0.95f);

        if (badgeText != null && !badgeText.isEmpty()) {
            if (active) {
                glColor4f(0.10f, 0.42f, 0.18f, 0.88f);
            } else {
                glColor4f(0.08f, 0.12f, 0.08f, 0.90f);
            }
            glBegin(GL_QUADS);
            glVertex2f(x + width - 50.0f, y + height - 18.0f);
            glVertex2f(x + width - 8.0f, y + height - 18.0f);
            glVertex2f(x + width - 8.0f, y + height - 3.0f);
            glVertex2f(x + width - 50.0f, y + height - 3.0f);
            glEnd();
            glColor3f(0.8f, 0.95f, 0.82f);
            drawText(badgeText, x + width - 43.0f, y + height - 5.0f, 0.80f);
        }

        if (active) {
            glColor4f(0.16f, 0.58f, 0.26f, 0.24f);
            glBegin(GL_QUADS);
            glVertex2f(x, y);
            glVertex2f(x + width, y);
            glVertex2f(x + width, y + height);
            glVertex2f(x, y + height);
            glEnd();
        }
    }

    @Override
    public void updateLayout() {
        width = getPreferredWidth();
        height = getPreferredHeight();
    }

    @Override
    public float getPreferredWidth() {
        return 120.0f * uiScale;
    }

    @Override
    public float getPreferredHeight() {
        return 60.0f * uiScale;
    }

    private void drawTexture(Texture texture, float x, float y, float width, float height) {
        if (texture == null) {
            return;
        }
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, texture.id());
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(x, y);
        glTexCoord2f(1, 0); glVertex2f(x + width, y);
        glTexCoord2f(1, 1); glVertex2f(x + width, y + height);
        glTexCoord2f(0, 1); glVertex2f(x, y + height);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);
    }

    private void drawText(String text, float x, float y, float scale) {
        if (text == null || text.isEmpty() || font == null) {
            return;
        }
        float[] color = new float[4];
        glGetFloatv(GL_CURRENT_COLOR, color);
        font.drawText(text, x, y, color[0], color[1], color[2], color[3], scale);
    }
}
