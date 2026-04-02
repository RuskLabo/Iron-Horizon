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
            glLineWidth(borderWidth * uiScale);
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
        // まず子要素のスケールを同期させ、各要素の推奨サイズを確定させる
        for (UiElement child : children) {
            child.setUiScale(uiScale);
            child.updateLayout();
        }

        // 自身のサイズ決定 (AUTOモード時のみ、推奨サイズで上書き)
        if (widthMode == LayoutMode.AUTO) {
            width = getPreferredWidth();
        }
        if (heightMode == LayoutMode.AUTO) {
            height = getPreferredHeight();
        }

        // 有効な描画範囲（自身の枠内 - パディング）
        float interiorW = width - (paddingLeft + paddingRight) * uiScale;
        float interiorH = height - (paddingTop + paddingBottom) * uiScale;

        // 子要素の座標をアライメントに合わせて更新
        for (UiElement child : children) {
            if (!child.visible) continue;

            // 水平位置
            float childWWithMargin = child.width + (child.marginLeft + child.marginRight) * uiScale;
            if (child.horizontalAlign == HorizontalAlign.CENTER) {
                child.x = x + (paddingLeft + child.marginLeft) * uiScale + (interiorW - childWWithMargin) / 2.0f;
            } else if (child.horizontalAlign == HorizontalAlign.RIGHT) {
                child.x = x + width - (paddingRight + child.marginRight) * uiScale - child.width;
            } else {
                child.x = x + (paddingLeft + child.marginLeft) * uiScale;
            }

            // 垂直位置
            float childHWithMargin = child.height + (child.marginTop + child.marginBottom) * uiScale;
            if (child.verticalAlign == VerticalAlign.MIDDLE) {
                child.y = y + (paddingTop + child.marginTop) * uiScale + (interiorH - childHWithMargin) / 2.0f;
            } else if (child.verticalAlign == VerticalAlign.BOTTOM) {
                child.y = y + height - (paddingBottom + child.marginBottom) * uiScale - child.height;
            } else {
                child.y = y + (paddingTop + child.marginTop) * uiScale;
            }
        }
    }

    @Override
    public float getPreferredWidth() {
        float maxChildW = 0;
        for (UiElement child : children) {
            if (!child.visible) continue;
            maxChildW = Math.max(maxChildW, child.getPreferredWidth() + (child.marginLeft + child.marginRight) * uiScale);
        }
        return maxChildW + (paddingLeft + paddingRight) * uiScale;
    }

    @Override
    public float getPreferredHeight() {
        float maxChildH = 0;
        for (UiElement child : children) {
            if (!child.visible) continue;
            maxChildH = Math.max(maxChildH, child.getPreferredHeight() + (child.marginTop + child.marginBottom) * uiScale);
        }
        return maxChildH + (paddingTop + paddingBottom) * uiScale;
    }
}
