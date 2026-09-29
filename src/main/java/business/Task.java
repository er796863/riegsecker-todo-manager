package business;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Task  implements Serializable {
    private Integer taskId;
    private String taskTitle;
    private String taskDescription;
    private LocalDateTime taskDateCreated;
    private LocalDateTime taskDateDue;

    public Task() {
    }

    public Task(Integer taskId, String taskTitle, String taskDescription, LocalDateTime taskDateCreated, LocalDateTime taskDateDue) {
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.taskDescription = taskDescription;
        this.taskDateCreated = taskDateCreated;
        this.taskDateDue = taskDateDue;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public LocalDateTime getTaskDateCreated() {
        return taskDateCreated;
    }

    public void setTaskDateCreated(LocalDateTime taskDateCreated) {
        this.taskDateCreated = taskDateCreated;
    }

    public LocalDateTime getTaskDateDue() {
        return taskDateDue;
    }

    public void setTaskDateDue(LocalDateTime taskDateDue) {
        this.taskDateDue = taskDateDue;
    }
}
