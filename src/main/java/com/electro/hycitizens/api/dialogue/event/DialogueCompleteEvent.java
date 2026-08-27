package com.electro.hycitizens.api.dialogue.event;

public class DialogueCompleteEvent extends DialogueEvent {
    private final String lastNodeId;
    private final String lastResponseId;

    public DialogueCompleteEvent(DialogueSessionSnapshot snapshot, String lastNodeId, String lastResponseId) {
        super(snapshot);
        this.lastNodeId = lastNodeId;
        this.lastResponseId = lastResponseId;
    }

    public String getLastNodeId() {
        return lastNodeId;
    }

    public String getLastResponseId() {
        return lastResponseId;
    }
}
