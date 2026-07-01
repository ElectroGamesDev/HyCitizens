package com.electro.hycitizens.api.dialogue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public class Dialogue implements IDialogue {
    private String documentType = "hycitizens:dialog";
    private String type = "hycitizens:dialog";
    private String id;
    private String title;
    private String startNode;
    private Map<String, IDialogueNode> nodes = new HashMap<>();
    private DialogResumePolicy resumePolicy = DialogResumePolicy.RESTART;
    private DialogPersistencePolicy persistencePolicy = new DialogPersistencePolicy();
    private DialogActionFailurePolicy actionFailurePolicy = DialogActionFailurePolicy.STOP;
    private String continuationProviderId;
    private int schemaVersion = 1;
    private long revision = 1;
    private String nextDialogueIdOnComplete;

    public Dialogue() {}
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getType() { return type != null ? type : "hycitizens:dialog"; }
    public void setType(String type) { this.type = type; }

    public Dialogue(String id, String title, String startNode) {
        this.id = id;
        this.title = title;
        this.startNode = startNode;
    }

    @Nonnull
    @Override
    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    @Nonnull
    @Override
    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    @Nonnull
    @Override
    public String getStartNodeId() { return startNode != null ? startNode : ""; }
    public void setStartNode(String startNode) { this.startNode = startNode; }

    @Nonnull
    @Override
    public Map<String, IDialogueNode> getNodes() {
        if (nodes == null) nodes = new LinkedHashMap<>();
        return Collections.unmodifiableMap(nodes);
    }
    public void setNodes(Map<String, IDialogueNode> nodes) {
        this.nodes = nodes != null ? new LinkedHashMap<>(nodes) : new LinkedHashMap<>();
    }
    public void putNode(IDialogueNode node) {
        if (nodes == null) nodes = new LinkedHashMap<>();
        nodes.put(node.getId(), node);
    }
    public void removeNode(String nodeId) {
        if (nodes != null) nodes.remove(nodeId);
    }

    @Nullable
    @Override
    public IDialogueNode getNode(@Nonnull String nodeId) {
        return nodes != null ? nodes.get(nodeId) : null;
    }

    @Nonnull
    @Override
    public DialogResumePolicy getResumePolicy() {
        return resumePolicy != null ? resumePolicy : DialogResumePolicy.RESTART;
    }
    public void setResumePolicy(DialogResumePolicy resumePolicy) { this.resumePolicy = resumePolicy; }
    @Nonnull
    @Override
    public DialogPersistencePolicy getPersistencePolicy() {
        if (persistencePolicy == null) persistencePolicy = new DialogPersistencePolicy();
        return persistencePolicy;
    }
    public void setPersistencePolicy(DialogPersistencePolicy persistencePolicy) { this.persistencePolicy = persistencePolicy; }
    @Nonnull
    @Override
    public DialogActionFailurePolicy getActionFailurePolicy() {
        return actionFailurePolicy != null ? actionFailurePolicy : DialogActionFailurePolicy.STOP;
    }
    public void setActionFailurePolicy(DialogActionFailurePolicy policy) { this.actionFailurePolicy = policy; }
    @Nullable
    @Override
    public String getContinuationProviderId() { return continuationProviderId; }
    public void setContinuationProviderId(String id) { continuationProviderId = id; }
    @Override
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    @Override
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }

    @Nullable
    @Override
    public String getNextDialogueIdOnComplete() { return nextDialogueIdOnComplete; }
    public void setNextDialogueIdOnComplete(@Nullable String nextDialogueIdOnComplete) { this.nextDialogueIdOnComplete = nextDialogueIdOnComplete; }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final Dialogue dialogue;

        public Builder(String id) {
            this.dialogue = new Dialogue();
            this.dialogue.setId(id);
        }

        public Builder title(String title) {
            this.dialogue.setTitle(title);
            return this;
        }

        public Builder startNode(String startNodeId) {
            this.dialogue.setStartNode(startNodeId);
            return this;
        }

        public Builder resumePolicy(DialogResumePolicy policy) {
            this.dialogue.setResumePolicy(policy);
            return this;
        }

        public Builder nextDialogueIdOnComplete(String nextDialogueIdOnComplete) {
            this.dialogue.setNextDialogueIdOnComplete(nextDialogueIdOnComplete);
            return this;
        }

        public Builder addNode(DialogueNode.Builder nodeBuilder) {
            DialogueNode node = nodeBuilder.build();
            this.dialogue.putNode(node);
            return this;
        }

        public Builder addNode(IDialogueNode node) {
            this.dialogue.putNode(node);
            return this;
        }

        public Dialogue build() {
            return dialogue;
        }
    }
}
