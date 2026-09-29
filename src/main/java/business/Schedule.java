package business;

import java.io.Serializable;

public class Schedule implements Serializable  {
    private Integer taskId;
    private Integer userId;
    private String notificationSettings;

    public Schedule() {
    }

    public Schedule(Integer taskId, Integer userId, String notificationSettings) {
        this.taskId = taskId;
        this.userId = userId;
        this.notificationSettings = notificationSettings;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getNotificationSettings() {
        return notificationSettings;
    }

    public void setNotificationSettings(String notificationSettings) {
        this.notificationSettings = notificationSettings;
    }
}
