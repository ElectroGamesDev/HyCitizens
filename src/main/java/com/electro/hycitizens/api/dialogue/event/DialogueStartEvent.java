package com.electro.hycitizens.api.dialogue.event;

public class DialogueStartEvent extends DialogueEvent {
    public DialogueStartEvent(DialogueSessionSnapshot snapshot) {
        super(snapshot);
    }
}
