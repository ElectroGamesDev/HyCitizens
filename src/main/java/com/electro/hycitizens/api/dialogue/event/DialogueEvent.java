package com.electro.hycitizens.api.dialogue.event;

public abstract class DialogueEvent {
    private final DialogueSessionSnapshot snapshot;

    protected DialogueEvent(DialogueSessionSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public DialogueSessionSnapshot getSnapshot() {
        return snapshot;
    }
}
