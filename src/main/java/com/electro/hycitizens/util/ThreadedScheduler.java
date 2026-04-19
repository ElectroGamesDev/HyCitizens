package com.electro.hycitizens.util;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThreadedScheduler {

    private static final Logger LOGGER = Logger.getLogger(ThreadedScheduler.class.getName());

    private Thread thread;
    private volatile boolean running = false;

    public void scheduleAtFixedRate(String name, Runnable task, long interval, TimeUnit unit) {
        scheduleAtFixedRate(name, task, 0, interval, unit);
    }

    public void scheduleAtFixedRate(String name, Runnable task, long initialDelay, long interval, TimeUnit unit) {
        if (running) {
            throw new IllegalStateException("ThreadedScheduler is already running. Call stop() first.");
        }

        long initialDelayMs = unit.toMillis(initialDelay);
        long intervalMs = unit.toMillis(interval);
        running = true;

        thread = new Thread(() -> {
            try {
                if (initialDelayMs > 0) {
                    Thread.sleep(initialDelayMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            while (!Thread.currentThread().isInterrupted() && running) {
                try {
                    long start = System.currentTimeMillis();
                    task.run();
                    long elapsed = System.currentTimeMillis() - start;
                    long sleepTime = intervalMs - elapsed;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "[ThreadedScheduler:" + name + "] Uncaught exception in task", e);
                }
            }
        });

        thread.setName(name);
        thread.setDaemon(true);
        thread.start();
    }

    public void scheduleWithFixedDelay(String name, Runnable task, long delay, TimeUnit unit) {
        if (running) {
            throw new IllegalStateException("ThreadedScheduler is already running. Call stop() first.");
        }

        long delayMs = unit.toMillis(delay);
        running = true;

        thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && running) {
                try {
                    task.run();
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "[ThreadedScheduler:" + name + "] Uncaught exception in task", e);
                }
            }
        });

        thread.setName(name);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public void stopAndAwait(long timeout, TimeUnit unit) throws InterruptedException {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread.join(unit.toMillis(timeout));
            thread = null;
        }
    }

    public boolean isRunning() {
        return running && thread != null && thread.isAlive();
    }
}