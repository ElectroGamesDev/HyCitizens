package com.electro.hycitizens.api.dialogue.event;

public interface DialogueListener {
    default void onDialoguePre(DialoguePreEvent event) {}
    default void onDialogueLifecycle(DialogueLifecycleEvent event) {}
    default void onDialogueStart(DialogueStartEvent event) {}
    default void onDialogueComplete(DialogueCompleteEvent event) {}
}
