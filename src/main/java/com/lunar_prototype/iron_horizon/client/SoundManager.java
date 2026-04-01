package com.lunar_prototype.iron_horizon.client;

import org.lwjgl.openal.*;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class SoundManager {
    private long device;
    private long context;
    private final Map<String, Integer> buffers = new HashMap<>();
    private final int[] sources = new int[16];
    private int nextSource = 0;
    private float masterVolume = 1.0f;

    public void init() {
        try {
            device = alcOpenDevice((ByteBuffer)null);
            if (device == NULL) return;
            ALCCapabilities deviceCaps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer)null);
            if (context == NULL) return;
            alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps);
            
            for (int i = 0; i < sources.length; i++) sources[i] = alGenSources();
            
            System.out.println("SoundManager: OpenAL Initialized.");

            loadSound("start", "/sounds/initating_battlefield_control.wav");
            loadSound("selected", "/sounds/unit_selected.wav");
            loadSound("move", "/sounds/action_rally_confirmed.wav");
            loadSound("attack", "/sounds/action_select_target.wav");
            loadSound("build", "/sounds/action_building.wav");
            loadSound("train", "/sounds/action_training.wav");
            loadSound("ready", "/sounds/unit_ready.wav");
            loadSound("victory", "/sounds/mission_accomplished.wav");
            loadSound("defeat", "/sounds/mission_failed.wav");
            loadSound("insufficient", "/sounds/insufficient_funds.wav");
            loadSound("under_attack", "/sounds/base_underattack.wav");
            
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void loadSound(String name, String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) return;
            AudioInputStream rawAis = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
            AudioFormat rawFormat = rawAis.getFormat();
            
            // Force conversion to 16-bit PCM which OpenAL supports
            AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                rawFormat.getSampleRate(),
                16,
                rawFormat.getChannels(),
                rawFormat.getChannels() * 2,
                rawFormat.getSampleRate(),
                false // Little Endian
            );
            
            AudioInputStream ais = AudioSystem.getAudioInputStream(targetFormat, rawAis);
            
            int alFormat = -1;
            if (targetFormat.getChannels() == 1) alFormat = AL_FORMAT_MONO16;
            else if (targetFormat.getChannels() == 2) alFormat = AL_FORMAT_STEREO16;

            byte[] data = ais.readAllBytes();
            ByteBuffer buffer = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
            buffer.put(data).flip();

            int bufferId = alGenBuffers();
            alBufferData(bufferId, alFormat, buffer, (int) targetFormat.getSampleRate());
            buffers.put(name, bufferId);
            System.out.println("SoundManager: Loaded & Converted " + name + " (16-bit PCM)");
            ais.close();
        } catch (Exception e) {
            System.err.println("SoundManager: Error loading " + path + ": " + e.getMessage());
        }
    }

    public void playSound(String name) {
        Integer bufferId = buffers.get(name);
        if (bufferId != null) {
            int source = sources[nextSource];
            alSourceStop(source);
            alSourcei(source, AL_BUFFER, bufferId);
            alSourcef(source, AL_GAIN, masterVolume);
            alSourcePlay(source);
            nextSource = (nextSource + 1) % sources.length;
        }
    }

    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0, Math.min(1, volume));
        for (int s : sources) alSourcef(s, AL_GAIN, masterVolume);
    }

    public float getMasterVolume() { return masterVolume; }

    public void cleanup() {
        if (context == NULL) return;
        for (int s : sources) alDeleteSources(s);
        for (int b : buffers.values()) alDeleteBuffers(b);
        alcMakeContextCurrent(NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }
}