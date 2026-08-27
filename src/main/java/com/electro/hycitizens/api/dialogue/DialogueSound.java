package com.electro.hycitizens.api.dialogue;

public class DialogueSound {
    private String id;
    private float pitch = 1.0f;
    private float volume = 1.0f;

    public DialogueSound() {}

    public DialogueSound(String id, float pitch, float volume) {
        this.id = id;
        this.pitch = pitch;
        this.volume = volume;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    public float getVolume() { return volume; }
    public void setVolume(float volume) { this.volume = volume; }
}
