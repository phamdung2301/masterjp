package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.document.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Document("custom_sets")
public class CustomSet {

    /** Gioi han chong spam (F-024). */
    public static final int MAX_WORDS = 500;
    public static final int MAX_SETS_PER_USER = 50;

    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;
    private String description;
    private List<String> wordIds = new ArrayList<>();
    private boolean isPublic = false;
    private int forkCount = 0;
    private Date createdAt = new Date();
    private Date updatedAt = new Date();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getWordIds() {
        return wordIds;
    }

    public void setWordIds(List<String> wordIds) {
        this.wordIds = wordIds;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public int getForkCount() {
        return forkCount;
    }

    public void setForkCount(int forkCount) {
        this.forkCount = forkCount;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
