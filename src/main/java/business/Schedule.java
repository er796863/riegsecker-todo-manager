package business;

import java.io.Serializable;
import java.util.Map;

public class Schedule implements Serializable {
    private String notificationSettings;
    private Map<Integer, Task> tasks;
    private User user;

    public Schedule() {
    }

    public Schedule(String notificationSettings, Map<Integer, Task> tasks, User user) {
        this.notificationSettings = notificationSettings;
        this.tasks = tasks;
        this.user = user;
    }

    public String getNotificationSettings() {
        return notificationSettings;
    }

    public void setNotificationSettings(String notificationSettings) {
        this.notificationSettings = notificationSettings;
    }

    public Map<Integer, Task> getTasks() {
        return tasks;
    }

    public void setTasks(Map<Integer, Task> tasks) {
        this.tasks = tasks;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
