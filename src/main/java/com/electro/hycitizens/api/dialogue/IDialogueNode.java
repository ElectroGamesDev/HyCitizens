package com.electro.hycitizens.api.dialogue;

import com.electro.hycitizens.api.scripting.ScriptAction;
import com.electro.hycitizens.api.scripting.ScriptCondition;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IDialogueNode {
    default boolean isCheckpoint() { return false; }
    @Nonnull
    String getId();

    @Nonnull
    String getSpeaker();

    @Nonnull
    String getText();

    @Nullable
    DialogueSound getSound();

    @Nonnull
    List<ScriptCondition> getConditions();

    @Nonnull
    List<ScriptAction> getActions();

    @Nonnull
    List<DialogueResponse> getResponses();

    @Nullable
    String getNextNodeId();
}
