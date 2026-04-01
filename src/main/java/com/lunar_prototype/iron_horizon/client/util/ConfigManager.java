package com.lunar_prototype.iron_horizon.client.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

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
}
