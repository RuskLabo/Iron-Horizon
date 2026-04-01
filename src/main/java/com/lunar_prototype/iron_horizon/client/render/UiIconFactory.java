package com.lunar_prototype.iron_horizon.client.render;

import com.lunar_prototype.iron_horizon.common.model.Building;
import com.lunar_prototype.iron_horizon.common.model.Unit;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public final class UiIconFactory {
    private static final int SIZE = 128;

    private UiIconFactory() {}

    public static Texture createBuildingIcon(Building.Type type) {
        return Texture.fromBufferedImage(renderBuildingIcon(type), false);
    }

    public static Texture createUnitIcon(Unit.Type type) {
        return Texture.fromBufferedImage(renderUnitIcon(type), false);
    }

    private static BufferedImage renderBuildingIcon(Building.Type type) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(image);
        Color accent = switch (type) {
            case FACTORY -> new Color(70, 190, 130);
            case WALL -> new Color(160, 170, 180);
            case EXTRACTOR -> new Color(230, 180, 80);
            case LASER_TOWER -> new Color(220, 110, 255);
            default -> new Color(120, 180, 140);
        };
        drawGlow(g, accent, 54, 72, 60, 34);
        switch (type) {
            case FACTORY -> drawFactory(g, accent);
            case WALL -> drawWall(g, accent);
            case EXTRACTOR -> drawExtractor(g, accent);
            case LASER_TOWER -> drawLaserTower(g, accent);
            default -> drawGenericBuilding(g, accent);
        }
        g.dispose();
        return image;
    }

    private static BufferedImage renderUnitIcon(Unit.Type type) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(image);
        Color accent = switch (type) {
            case TANK -> new Color(80, 150, 255);
            case HOUND -> new Color(65, 220, 255);
            case OBELISK -> new Color(210, 110, 255);
            case CONSTRUCTOR -> new Color(135, 255, 145);
        };
        drawGlow(g, accent, 62, 74, 68, 34);
        switch (type) {
            case TANK -> drawTank(g, accent);
            case HOUND -> drawHound(g, accent);
            case OBELISK -> drawObelisk(g, accent);
            case CONSTRUCTOR -> drawConstructor(g, accent);
        }
        g.dispose();
        return image;
    }

    private static Graphics2D prepare(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        return g;
    }

    private static void drawGlow(Graphics2D g, Color accent, int cx, int cy, int w, int h) {
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 42));
        g.fillOval(cx - w / 2, cy - h / 2, w, h);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 18));
        g.fillOval(cx - w, cy - h, w * 2, h * 2);
    }

    private static void drawShadow(Graphics2D g, Shape shape) {
        g.setColor(new Color(0, 0, 0, 115));
        g.translate(4, 4);
        g.fill(shape);
        g.translate(-4, -4);
    }

    private static void fillWithStroke(Graphics2D g, Shape shape, Color fill, Color outline) {
        g.setPaint(new GradientPaint(0, 0, lighten(fill, 0.28f), 0, 96, darken(fill, 0.24f)));
        g.fill(shape);
        g.setColor(outline);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    private static Color lighten(Color color, float amount) {
        return new Color(
                clamp(color.getRed() + (int) ((255 - color.getRed()) * amount)),
                clamp(color.getGreen() + (int) ((255 - color.getGreen()) * amount)),
                clamp(color.getBlue() + (int) ((255 - color.getBlue()) * amount)),
                color.getAlpha());
    }

    private static Color darken(Color color, float amount) {
        return new Color(
                clamp((int) (color.getRed() * (1.0f - amount))),
                clamp((int) (color.getGreen() * (1.0f - amount))),
                clamp((int) (color.getBlue() * (1.0f - amount))),
                color.getAlpha());
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void drawFactory(Graphics2D g, Color accent) {
        Shape body = new RoundRectangle2D.Float(30, 48, 68, 34, 8, 8);
        Shape roof = polygon(24, 48, 41, 31, 87, 31, 104, 48);
        Shape chimney = new RoundRectangle2D.Float(76, 21, 14, 24, 5, 5);
        drawShadow(g, body);
        drawShadow(g, roof);
        drawShadow(g, chimney);
        fillWithStroke(g, body, accent, darken(accent, 0.45f));
        fillWithStroke(g, roof, lighten(accent, 0.15f), darken(accent, 0.50f));
        fillWithStroke(g, chimney, darken(accent, 0.12f), darken(accent, 0.55f));
        g.setColor(new Color(255, 255, 255, 170));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(40, 58, 88, 58);
        g.drawLine(40, 69, 88, 69);
        g.setColor(new Color(255, 255, 255, 85));
        g.drawRect(45, 39, 16, 10);
    }

    private static void drawWall(Graphics2D g, Color accent) {
        Shape base = new RoundRectangle2D.Float(26, 48, 76, 32, 6, 6);
        drawShadow(g, base);
        fillWithStroke(g, base, accent, darken(accent, 0.5f));
        g.setColor(new Color(255, 255, 255, 150));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(38, 58, 90, 58);
        g.drawLine(38, 69, 90, 69);
        g.drawLine(52, 48, 52, 80);
        g.drawLine(72, 48, 72, 80);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(32, 52, 14, 10, 4, 4);
        g.fillRoundRect(60, 52, 14, 10, 4, 4);
        g.fillRoundRect(88, 52, 8, 10, 4, 4);
    }

    private static void drawExtractor(Graphics2D g, Color accent) {
        Shape base = new Ellipse2D.Float(35, 44, 58, 42);
        Shape mast = new RoundRectangle2D.Float(56, 28, 16, 42, 8, 8);
        Shape drill = polygon(64, 22, 76, 42, 64, 54, 52, 42);
        drawShadow(g, base);
        drawShadow(g, mast);
        drawShadow(g, drill);
        fillWithStroke(g, base, accent, darken(accent, 0.45f));
        fillWithStroke(g, mast, darken(accent, 0.06f), darken(accent, 0.55f));
        fillWithStroke(g, drill, lighten(accent, 0.22f), darken(accent, 0.55f));
        g.setColor(new Color(255, 255, 255, 170));
        g.setStroke(new BasicStroke(3f));
        g.drawArc(42, 54, 44, 22, 200, 150);
        g.drawArc(42, 54, 44, 22, 20, 150);
        g.drawLine(64, 58, 64, 78);
    }

    private static void drawLaserTower(Graphics2D g, Color accent) {
        Shape base = new RoundRectangle2D.Float(42, 70, 44, 16, 5, 5);
        Shape tower = polygon(50, 68, 56, 26, 72, 26, 78, 68);
        Shape lens = new Ellipse2D.Float(57, 18, 14, 14);
        drawShadow(g, base);
        drawShadow(g, tower);
        drawShadow(g, lens);
        fillWithStroke(g, base, darken(accent, 0.05f), darken(accent, 0.55f));
        fillWithStroke(g, tower, accent, darken(accent, 0.5f));
        fillWithStroke(g, lens, lighten(accent, 0.26f), darken(accent, 0.55f));
        g.setColor(new Color(255, 255, 255, 170));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(64, 32, 88, 16);
        g.drawLine(64, 32, 102, 26);
        g.drawLine(64, 32, 96, 42);
        g.setColor(new Color(255, 255, 255, 105));
        g.drawLine(60, 70, 68, 24);
    }

    private static void drawTank(Graphics2D g, Color accent) {
        Shape tracks = new RoundRectangle2D.Float(24, 58, 80, 24, 10, 10);
        Shape body = new RoundRectangle2D.Float(36, 42, 58, 28, 12, 12);
        Shape turret = new Ellipse2D.Float(52, 30, 28, 28);
        Shape barrel = new RoundRectangle2D.Float(74, 40, 28, 8, 4, 4);
        drawShadow(g, tracks);
        drawShadow(g, body);
        drawShadow(g, turret);
        drawShadow(g, barrel);
        fillWithStroke(g, tracks, darken(accent, 0.28f), darken(accent, 0.62f));
        fillWithStroke(g, body, accent, darken(accent, 0.55f));
        fillWithStroke(g, turret, lighten(accent, 0.1f), darken(accent, 0.55f));
        fillWithStroke(g, barrel, lighten(accent, 0.22f), darken(accent, 0.55f));
        g.setColor(new Color(255, 255, 255, 110));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(30, 68, 96, 68);
        g.drawLine(30, 74, 96, 74);
    }

    private static void drawHound(Graphics2D g, Color accent) {
        Shape tracks = new RoundRectangle2D.Float(26, 58, 78, 20, 10, 10);
        Shape body = polygon(34, 56, 52, 38, 92, 42, 102, 56, 62, 72, 40, 70);
        Shape cab = polygon(42, 56, 58, 44, 76, 48, 64, 62, 48, 62);
        Shape nose = polygon(88, 42, 104, 50, 98, 60, 84, 54);
        drawShadow(g, tracks);
        drawShadow(g, body);
        drawShadow(g, cab);
        drawShadow(g, nose);
        fillWithStroke(g, tracks, darken(accent, 0.2f), darken(accent, 0.62f));
        fillWithStroke(g, body, accent, darken(accent, 0.52f));
        fillWithStroke(g, cab, lighten(accent, 0.14f), darken(accent, 0.52f));
        fillWithStroke(g, nose, lighten(accent, 0.28f), darken(accent, 0.55f));
        g.setColor(new Color(255, 255, 255, 135));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(40, 54, 84, 48);
    }

    private static void drawConstructor(Graphics2D g, Color accent) {
        Shape tracks = new RoundRectangle2D.Float(26, 60, 76, 20, 10, 10);
        Shape chassis = new RoundRectangle2D.Float(38, 44, 54, 24, 10, 10);
        Shape boom = polygon(64, 46, 74, 26, 78, 28, 70, 50);
        Shape arm = polygon(70, 28, 92, 20, 96, 26, 76, 34);
        Shape hook = new Ellipse2D.Float(90, 24, 8, 8);
        drawShadow(g, tracks);
        drawShadow(g, chassis);
        drawShadow(g, boom);
        drawShadow(g, arm);
        drawShadow(g, hook);
        fillWithStroke(g, tracks, darken(accent, 0.24f), darken(accent, 0.62f));
        fillWithStroke(g, chassis, accent, darken(accent, 0.55f));
        fillWithStroke(g, boom, lighten(accent, 0.12f), darken(accent, 0.52f));
        fillWithStroke(g, arm, lighten(accent, 0.20f), darken(accent, 0.52f));
        fillWithStroke(g, hook, lighten(accent, 0.32f), darken(accent, 0.55f));
        g.setColor(new Color(255, 255, 255, 140));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(44, 55, 86, 55);
        g.drawLine(46, 62, 84, 62);
    }

    private static void drawObelisk(Graphics2D g, Color accent) {
        Shape crystal = polygon(64, 16, 92, 56, 78, 110, 50, 110, 36, 56);
        Shape core = polygon(64, 28, 80, 58, 70, 96, 58, 96, 48, 58);
        Shape base = new RoundRectangle2D.Float(34, 98, 60, 14, 8, 8);
        drawShadow(g, crystal);
        drawShadow(g, core);
        drawShadow(g, base);
        fillWithStroke(g, base, darken(accent, 0.2f), darken(accent, 0.62f));
        fillWithStroke(g, crystal, accent, darken(accent, 0.52f));
        fillWithStroke(g, core, lighten(accent, 0.16f), darken(accent, 0.52f));
        g.setColor(new Color(255, 255, 255, 170));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(64, 24, 64, 102);
        g.drawLine(52, 48, 76, 48);
        g.drawLine(46, 66, 82, 66);
        g.drawLine(40, 84, 88, 84);
    }

    private static void drawGenericBuilding(Graphics2D g, Color accent) {
        Shape shape = new RoundRectangle2D.Float(30, 40, 68, 42, 10, 10);
        drawShadow(g, shape);
        fillWithStroke(g, shape, accent, darken(accent, 0.5f));
    }

    private static Shape polygon(int... points) {
        Path2D path = new Path2D.Float();
        if (points.length < 4) {
            return path;
        }
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) {
            path.lineTo(points[i], points[i + 1]);
        }
        path.closePath();
        return path;
    }
}
