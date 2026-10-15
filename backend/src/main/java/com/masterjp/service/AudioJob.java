package com.masterjp.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Trang thai job regenerate audio (FR-033). Phase 1 luu in-memory
 * (khong can realtime socket — FE poll GET /api/admin/audio/jobs/{jobId}).
 */
public class AudioJob {

    private final String jobId;
    private final List<String> wordIds;
    private volatile String status = "QUEUED"; // QUEUED | RUNNING | DONE | FAILED
    private final AtomicInteger done = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final List<AudioJobItem> items = new ArrayList<>();
    private final Date createdAt = new Date();

    public AudioJob(String jobId, List<String> wordIds) {
        this.jobId = jobId;
        this.wordIds = List.copyOf(wordIds);
    }

    public synchronized void itemDone(String wordId, String itemStatus, String message) {
        items.add(new AudioJobItem(wordId, itemStatus, message));
        done.incrementAndGet();
        if ("FAILED".equals(itemStatus)) {
            failed.incrementAndGet();
        }
    }

    public String getJobId() {
        return jobId;
    }

    public List<String> getWordIds() {
        return wordIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotal() {
        return wordIds.size();
    }

    public int getDone() {
        return done.get();
    }

    public int getFailed() {
        return failed.get();
    }

    public synchronized List<AudioJobItem> getItems() {
        return List.copyOf(items);
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public record AudioJobItem(String wordId, String status, String message) {
    }
}
