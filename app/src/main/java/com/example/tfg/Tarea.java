package com.example.tfg;

import com.google.firebase.firestore.Exclude;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Tarea implements Serializable {
    private String id;
    private String title;
    private String description;
    private String notas;
    private String category;
    private String userId;
    private String userEmail;
    private String parentId;
    private Long dueDate;
    private boolean hasTime;
    private String assignedTo;
    private String lastModifiedBy;
    private String lastModifiedByEmail;
    private boolean completed;
    private int totalSubtasks;
    private int completedSubtasks;
    private List<Tarea> subTasks;
    private List<String> sharedWith;

    public Tarea() {
        this.subTasks = new ArrayList<>();
        this.sharedWith = new ArrayList<>();
    }

    public Tarea(String title, String description, String category, String userId, String parentId, Long dueDate) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.userId = userId;
        this.parentId = parentId;
        this.dueDate = dueDate;
        this.completed = false;
        this.totalSubtasks = 0;
        this.completedSubtasks = 0;
        this.subTasks = new ArrayList<>();
        this.sharedWith = new ArrayList<>();
    }


    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public Long getDueDate() { return dueDate; }
    public void setDueDate(Long dueDate) { this.dueDate = dueDate; }

    public boolean isHasTime() { return hasTime; }
    public void setHasTime(boolean hasTime) { this.hasTime = hasTime; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }

    public String getLastModifiedByEmail() { return lastModifiedByEmail; }
    public void setLastModifiedByEmail(String lastModifiedByEmail) { this.lastModifiedByEmail = lastModifiedByEmail; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public int getTotalSubtasks() { return totalSubtasks; }
    public void setTotalSubtasks(int totalSubtasks) { this.totalSubtasks = totalSubtasks; }

    public int getCompletedSubtasks() { return completedSubtasks; }
    public void setCompletedSubtasks(int completedSubtasks) { this.completedSubtasks = completedSubtasks; }

    @Exclude
    public int getProgress() {
        if (totalSubtasks == 0) return completed ? 100 : 0;
        return (completedSubtasks * 100) / totalSubtasks;
    }

    @Exclude
    public List<Tarea> getSubTasks() { return subTasks; }
    public void setSubTasks(List<Tarea> subTasks) { this.subTasks = subTasks; }

    public List<String> getSharedWith() { return sharedWith; }
    public void setSharedWith(List<String> sharedWith) { this.sharedWith = sharedWith; }
}
