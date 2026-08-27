package com.electro.hycitizens.api.dialogue;

import javax.annotation.Nullable;

@FunctionalInterface
public interface DialogContinuationProvider {
    @Nullable
    String resolveStartNode(DialogueSession session, PlayerDialogState state);
}
