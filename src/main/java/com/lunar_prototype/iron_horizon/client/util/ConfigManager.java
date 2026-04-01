package com.lunar_prototype.iron_horizon.client.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import com.lunar_prototype.iron_horizon.client.render.FsrPreset;

public class ConfigManager {
    private static final String CONFIG_FILE = System.getProperty("user.home") + File.separator + ".iron_horizon" + File.separator + "config.properties";
    private final Properties properties = new Properties();

    public ConfigManager() {
        load();
    }

    public void load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void save() {
        File file = new File(CONFIG_FILE);
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            properties.store(out, "Iron Horizon Client Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getServerIp() {
        return properties.getProperty("server.ip", "localhost");
    }

    public void setServerIp(String ip) {
        properties.setProperty("server.ip", ip);
    }

    public String getUsername() {
        return properties.getProperty("username", "Player" + (int)(Math.random() * 1000));
    }

    public void setUsername(String username) {
        properties.setProperty("username", username);
    }
    
    public boolean hasInitialConfig() {
        return properties.containsKey("server.ip") && properties.containsKey("username");
    }

    public boolean isFsrEnabled() {
        return Boolean.parseBoolean(properties.getProperty("fsr.enabled", "false"));
    }

    public void setFsrEnabled(boolean enabled) {
        properties.setProperty("fsr.enabled", Boolean.toString(enabled));
    }

    public FsrPreset getFsrPreset() {
        String stored = properties.getProperty("fsr.preset", FsrPreset.QUALITY.name());
        try {
            return FsrPreset.valueOf(stored);
        } catch (IllegalArgumentException ex) {
            return FsrPreset.QUALITY;
        }
    }

    public void setFsrPreset(FsrPreset preset) {
        properties.setProperty("fsr.preset", preset.name());
    }

    public float getFsrSharpness() {
        String stored = properties.getProperty("fsr.sharpness", "0.2");
        try {
            return clamp(Float.parseFloat(stored), 0.0f, 2.0f);
        } catch (NumberFormatException ex) {
            return 0.2f;
        }
    }

    public void setFsrSharpness(float sharpness) {
        properties.setProperty("fsr.sharpness", Float.toString(clamp(sharpness, 0.0f, 2.0f)));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
