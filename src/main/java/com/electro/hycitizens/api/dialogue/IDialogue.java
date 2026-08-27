package com.electro.hycitizens.api.dialogue;

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IDialogue {
    @Nonnull
    String getId();

    @Nonnull
    String getTitle();

    @Nonnull
    String getStartNodeId();

    @Nonnull
    Map<String, IDialogueNode> getNodes();

    @Nullable
    IDialogueNode getNode(@Nonnull String nodeId);

    @Nonnull
    DialogResumePolicy getResumePolicy();

    @Nonnull
    DialogPersistencePolicy getPersistencePolicy();

    @Nonnull
    DialogActionFailurePolicy getActionFailurePolicy();

    int getSchemaVersion();

    long getRevision();

    @Nullable
    String getContinuationProviderId();

    @Nullable
    String getNextDialogueIdOnComplete();
}
