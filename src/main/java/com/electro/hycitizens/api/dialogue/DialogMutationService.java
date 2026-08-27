package com.electro.hycitizens.api.dialogue;

import com.electro.hycitizens.api.scripting.ScriptAction;
import com.electro.hycitizens.api.scripting.ScriptCondition;
import com.google.gson.Gson;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

public final class DialogMutationService {
    private final Map<UUID, DialogPatch> patches = new ConcurrentHashMap<>();
    private final Map<String, String> materializedCache = new ConcurrentHashMap<>();
    private final Gson gson;

    public DialogMutationService(Gson gson) {
        this.gson = gson;
    }

    public UUID apply(DialogPatch patch) {
        patches.put(patch.id(), patch);
        materializedCache.clear();
        return patch.id();
    }

    public boolean remove(UUID patchId) {
        boolean removed = patches.remove(patchId) != null;
        if (removed) materializedCache.clear();
        return removed;
    }

    public void removeByOwner(String owner) {
        patches.values().removeIf(patch -> Objects.equals(owner, patch.owner()));
        materializedCache.clear();
    }

    public IDialogue materialize(IDialogue base, String npcId, UUID playerId, UUID sessionId) {
        long now = System.currentTimeMillis();
        List<DialogPatch> applicable = patches.values().stream()
                .filter(patch -> Objects.equals(patch.dialogId(), base.getId()))
                .filter(patch -> !patch.isExpired(now))
                .filter(patch -> matches(patch, npcId, playerId, sessionId))
                .sorted(Comparator.comparingInt(DialogPatch::priority).thenComparing(patch -> patch.id().toString()))
                .toList();
        String key = base.getId() + ":" + base.getRevision() + ":" + applicable.stream()
                .map(patch -> patch.id() + "@" + patch.priority()).toList();
        String serialized = materializedCache.computeIfAbsent(key, ignored -> {
            IDialogue copy = gson.fromJson(gson.toJson(base, IDialogue.class), IDialogue.class);
            if (copy instanceof Dialogue standard) {
                applicable.forEach(patch -> applyTo(standard, patch));
            } else if (!applicable.isEmpty()) {
                throw new IllegalArgumentException("Mutation overlays require a compatible mutable codec for dialog type "
                        + copy.getClass().getName());
            }
            return gson.toJson(copy, IDialogue.class);
        });
        return gson.fromJson(serialized, IDialogue.class);
    }

    public Collection<DialogPatch> snapshot() {
        return List.copyOf(patches.values());
    }

    private boolean matches(DialogPatch patch, String npcId, UUID playerId, UUID sessionId) {
        return switch (patch.scope()) {
            case GLOBAL -> true;
            case NPC -> Objects.equals(patch.scopeId(), npcId);
            case PLAYER -> Objects.equals(patch.scopeId(), playerId.toString());
            case SESSION -> Objects.equals(patch.scopeId(), sessionId.toString());
        };
    }

    private void applyTo(Dialogue dialog, DialogPatch patch) {
        if (patch.operation() == DialogPatch.Operation.INJECT_NODE) {
            DialogueNode injected = gson.fromJson(patch.value(), DialogueNode.class);
            if (injected != null && !injected.getId().isEmpty()) dialog.putNode(injected);
            return;
        }
        IDialogueNode rawNode = dialog.getNode(patch.nodeId());
        if (!(rawNode instanceof DialogueNode node)) {
            return;
        }
        switch (patch.operation()) {
            case REPLACE_TEXT -> node.setText(patch.value());
            case REPLACE_SPEAKER -> node.setSpeaker(patch.value());
            case REPLACE_SOUND -> node.setSound(gson.fromJson(patch.value(), DialogueSound.class));
            case REDIRECT_NODE -> node.setNextNode(patch.value());
            case HIDE_NODE -> dialog.removeNode(patch.nodeId());
            case ADD_RESPONSE -> node.addResponse(gson.fromJson(patch.value(), DialogueResponse.class));
            case REMOVE_RESPONSE -> node.removeResponsesById(patch.responseId());
            case REPLACE_RESPONSE_TEXT -> node.getResponses().stream()
                    .filter(response -> Objects.equals(response.getId(), patch.responseId()))
                    .findFirst()
                    .ifPresent(response -> response.setText(patch.value()));
            case ADD_CONDITION -> node.addCondition(
                    gson.fromJson(patch.value(), ScriptCondition.class));
            case REMOVE_CONDITION -> removeIndexed(node::removeCondition, patch.value());
            case ADD_ACTION -> node.addAction(
                    gson.fromJson(patch.value(), ScriptAction.class));
            case REMOVE_ACTION -> removeIndexed(node::removeAction, patch.value());
            case INJECT_NODE -> { }
        }
    }

    private void removeIndexed(IntConsumer remover, String rawIndex) {
        try {
            int index = Integer.parseInt(rawIndex);
            if (index >= 0) remover.accept(index);
        } catch (NumberFormatException ignored) {}
    }
}
