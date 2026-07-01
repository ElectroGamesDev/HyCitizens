package com.electro.hycitizens.api.dialogue;

public class DialogPersistencePolicy {
    private boolean persistOnDismiss = true;
    private boolean persistOnDisconnect = true;
    private boolean persistOnShutdown = true;
    private boolean persistOnReload = true;
    private boolean clearOnExpiry = true;
    private boolean clearOnCompletion = true;
    private long sessionTtlMillis = 86_400_000L;
    private DialogCheckpointStrategy checkpointStrategy = DialogCheckpointStrategy.EVERY_NODE;

    public boolean isPersistOnDismiss() { return persistOnDismiss; }
    public void setPersistOnDismiss(boolean value) { persistOnDismiss = value; }
    public boolean isPersistOnDisconnect() { return persistOnDisconnect; }
    public void setPersistOnDisconnect(boolean value) { persistOnDisconnect = value; }
    public boolean isPersistOnShutdown() { return persistOnShutdown; }
    public void setPersistOnShutdown(boolean value) { persistOnShutdown = value; }
    public boolean isPersistOnReload() { return persistOnReload; }
    public void setPersistOnReload(boolean value) { persistOnReload = value; }
    public boolean isClearOnExpiry() { return clearOnExpiry; }
    public void setClearOnExpiry(boolean value) { clearOnExpiry = value; }
    public boolean isClearOnCompletion() { return clearOnCompletion; }
    public void setClearOnCompletion(boolean value) { clearOnCompletion = value; }
    public long getSessionTtlMillis() { return sessionTtlMillis; }
    public void setSessionTtlMillis(long value) { sessionTtlMillis = Math.max(1_000L, value); }
    public DialogCheckpointStrategy getCheckpointStrategy() {
        return checkpointStrategy != null ? checkpointStrategy : DialogCheckpointStrategy.EVERY_NODE;
    }
    public void setCheckpointStrategy(DialogCheckpointStrategy value) { checkpointStrategy = value; }
}
