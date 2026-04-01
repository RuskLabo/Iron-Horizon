package com.lunar_prototype.iron_horizon.client.ui;

import static org.lwjgl.opengl.GL11.*;

public class UiBox extends UiContainer {
    public float bgR = 0.14f, bgG = 0.18f, bgB = 0.14f, bgA = 0.92f;
    public float borderR = 0.34f, borderG = 0.85f, borderB = 0.45f, borderA = 0.35f;
    public float borderWidth = 2.0f;
    public boolean drawBorder = true;

    @Override
    public void render() {
        if (!visible) return;

        // 背景描画
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glColor4f(bgR, bgG, bgB, bgA);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        // 枠線描画
        if (drawBorder) {
            glColor4f(borderR, borderG, borderB, borderA);
            glLineWidth(borderWidth);
            glBegin(GL_LINE_LOOP);
            glVertex2f(x, y);
            glVertex2f(x + width, y);
            glVertex2f(x + width, y + height);
            glVertex2f(x, y + height);
            glEnd();
        }

        super.render(); // 子要素の描画
    }

    @Override
    public void updateLayout() {
        // 子要素のサイズに基づいて自身のサイズを決定（もし固定サイズでなければ）
        float prefW = 0;
        float prefH = 0;
        for (UiElement child : children) {
            if (!child.visible) continue;
            prefW = Math.max(prefW, child.getPreferredWidth() + child.marginLeft + child.marginRight);
            prefH = Math.max(prefH, child.getPreferredHeight() + child.marginTop + child.marginBottom);
        }
        
        width = prefW + paddingLeft + paddingRight;
        height = prefH + paddingTop + paddingBottom;

        // 子要素の座標を自身のパディングに合わせて更新
        for (UiElement child : children) {
            if (!child.visible) continue;
            child.x = x + paddingLeft + child.marginLeft;
            child.y = y + paddingTop + child.marginTop;
            child.width = child.getPreferredWidth();
            child.height = child.getPreferredHeight();
            child.updateLayout();
        }
    }

    @Override
    public float getPreferredWidth() {
        float maxChildW = 0;
        for (UiElement child : children) {
            if (!child.visible) continue;
            maxChildW = Math.max(maxChildW, child.getPreferredWidth() + child.marginLeft + child.marginRight);
        }
        return maxChildW + paddingLeft + paddingRight;
    }

    @Override
    public float getPreferredHeight() {
        float maxChildH = 0;
        for (UiElement child : children) {
            if (!child.visible) continue;
            maxChildH = Math.max(maxChildH, child.getPreferredHeight() + child.marginTop + child.marginBottom);
        }
        return maxChildH + paddingTop + paddingBottom;
    }
}
