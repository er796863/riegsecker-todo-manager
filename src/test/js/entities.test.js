import { beforeEach, describe, expect, it, vi } from "vitest";

const entitiesScript = "../../main/webapp/shared/entities.js";

function setPage({ entity = "tasks", mode = "list", label = "Tasks" } = {}) {
  document.body.innerHTML = `
    <main data-entity="${entity}" data-mode="${mode}" data-label="${label}">
      <output id="status"></output>
      <section id="results"></section>
      <form id="search-form"><input id="search"></form>
    </main>
  `;
}

async function loadEntitiesPage() {
  const fetcher = globalThis.fetch;
  vi.stubGlobal("fetch", (input, ...options) => {
    if (String(input).includes("/api/auth/session")) {
      return Promise.resolve({
        ok: true,
        json: async () => ({
          authenticated: true,
          user: { userId: 1, firstName: "Jane", lastName: "Doe", role: "ADMIN" },
        }),
      });
    }
    return fetcher(input, ...options);
  });
  vi.resetModules();
  await import(entitiesScript);
}

describe("entity pages", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    history.replaceState(null, "", "/tasks/index.html");
  });

  it("renders records and reports the count", async () => {
    setPage();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          1: {
            taskId: 1,
            taskTitle: "Plan tests",
            taskDateDue: "2026-10-16T12:00",
          },
        }),
      }),
    );

    await loadEntitiesPage();

    expect(document.querySelector("#results h2").textContent).toBe("Plan tests");
    expect(document.querySelector("#results a").getAttribute("href")).toBe(
      "detail.html?id=1",
    );
    expect(document.querySelector("#status").textContent).toBe("1 record found.");
  });

  it("creates a task from the management form and refreshes the list", async () => {
    setPage();
    const fetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ 1: { taskId: 1, taskTitle: "Old task" } }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ taskId: 2 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          2: { taskId: 2, taskTitle: "New task", taskDateDue: null },
        }),
      });
    vi.stubGlobal("fetch", fetch);
    await loadEntitiesPage();

    document.querySelector('[name="taskTitle"]').value = "New task";
    document
      .querySelector(".entity-form")
      .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() =>
      expect(fetch).toHaveBeenNthCalledWith(
        2,
        "../api/business/tasks",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({
            taskTitle: "New task",
            taskDescription: null,
            taskDateDue: null,
          }),
        }),
      ),
    );
    await vi.waitFor(() =>
      expect(document.querySelector("#results h2").textContent).toBe("New task"),
    );
  });

  it("loads all records and links admin records to their all-scope details", async () => {
    document.body.innerHTML = `
      <main data-entity="tasks" data-mode="list" data-label="Tasks" data-scope="all" data-detail-base="../tasks/">
        <output id="status"></output>
        <section id="results"></section>
      </main>
    `;
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        4: { taskId: 4, taskTitle: "Review", taskDateDue: null },
      }),
    });
    vi.stubGlobal("fetch", fetch);
    await loadEntitiesPage();

    expect(fetch).toHaveBeenCalledWith("../api/business/tasks?scope=all");
    expect(document.querySelector("#results a").getAttribute("href")).toBe(
      "../tasks/detail.html?id=4&scope=all",
    );
  });

  it("shows user management actions only for accounts other than the signed-in admin", async () => {
    document.body.innerHTML = `
      <main data-entity="users" data-mode="list" data-label="Users" data-scope="all" data-detail-base="../users/">
        <output id="status"></output>
        <section id="results"></section>
      </main>
    `;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          1: { userId: 1, userName: "admin", firstName: "Alex", lastName: "Admin", email: "admin@example.com", role: "ADMIN" },
          2: { userId: 2, userName: "jdoe", firstName: "Jane", lastName: "Doe", email: "jane@example.com", role: "USER" },
        }),
      }),
    );
    await loadEntitiesPage();

    const records = document.querySelectorAll("#results article");
    expect(records).toHaveLength(2);
    expect(records[0].querySelector(".record-actions")).toBeNull();
    expect(records[1].querySelectorAll(".record-actions button")).toHaveLength(2);
  });

  it("uses the current user's scope by default even if the session user is an admin", async () => {
    setPage();
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        4: { taskId: 4, taskTitle: "Personal task", taskDateDue: null },
      }),
    });
    vi.stubGlobal("fetch", fetch);
    await loadEntitiesPage();

    expect(fetch).toHaveBeenCalledWith("../api/business/tasks");
    expect(document.querySelector("#results h2").textContent).toBe("Personal task");
  });

  it("requests all-record scope when opening an admin record detail", async () => {
    setPage({ mode: "detail", label: "Task" });
    history.replaceState(null, "", "/tasks/detail.html?id=4&scope=all");
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        taskId: 4,
        taskTitle: "Another user's task",
        taskDescription: null,
      }),
    });
    vi.stubGlobal("fetch", fetch);
    await loadEntitiesPage();

    expect(fetch).toHaveBeenCalledWith("../api/business/tasks?id=4&scope=all");
    expect(document.querySelector("#results").textContent).toContain("Another user's task");
  });

  it("uses the current user's scope by default even if the session user is an admin", async () => {
    setPage();
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        4: { taskId: 4, taskTitle: "Review", taskDateDue: null },
      }),
    });
    vi.stubGlobal("fetch", fetch);
    await loadEntitiesPage();

    expect(fetch).toHaveBeenCalledWith("../api/business/tasks");
    expect(document.querySelector("#results h2").textContent).toBe("Review");
  });

  it("shows API errors without inserting response text as markup", async () => {
    setPage();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => ({ error: "<b>Database unavailable</b>" }),
      }),
    );

    await loadEntitiesPage();

    expect(document.querySelector("#status").textContent).toBe(
      "<b>Database unavailable</b>",
    );
    expect(document.querySelector("#status b")).toBeNull();
  });

  it("loads details from the page query and renders null values as a dash", async () => {
    setPage({ mode: "detail", label: "Task" });
    history.replaceState(null, "", "/tasks/detail.html?id=4");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          taskId: 4,
          taskTitle: "Plan tests",
          taskDescription: null,
        }),
      }),
    );

    await loadEntitiesPage();

    expect(document.querySelector("#results").textContent).toContain("Task Title");
    expect(document.querySelector("#results").textContent).toContain("-");
    expect(document.querySelector("#status").textContent).toBe(
      "Task details loaded.",
    );
  });

  it("groups schedule tasks under the associated user", async () => {
    setPage({ entity: "schedules", label: "Schedules" });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          3: {
            notificationSettings: "DAILY",
            user: { userId: 3, firstName: "Jane", lastName: "Doe", userName: "jdoe" },
            tasks: {
              8: { taskId: 8, taskTitle: "Review", taskDateDue: null },
            },
          },
        }),
      }),
    );

    await loadEntitiesPage();

    expect(document.querySelector("#results summary").textContent).toBe(
      "Jane Doe (@jdoe)",
    );
    expect(document.querySelector("#results h2").textContent).toBe("Review");
    expect(document.querySelector("#results a").getAttribute("href")).toBe(
      "detail.html?id=3",
    );
  });

  it("edits schedule weekdays, send time, and time zone", async () => {
    setPage({ entity: "schedules", label: "Schedules" });
    const schedule = {
      notificationSettings: "WEEKLY",
      notificationsEnabled: true,
      notificationDays: ["MONDAY", "WEDNESDAY"],
      notificationTime: "08:30",
      notificationTimezone: "America/Denver",
      user: { userId: 3, firstName: "Jane", lastName: "Doe", userName: "jdoe" },
      tasks: {
        8: { taskId: 8, taskTitle: "Review project", taskDateDue: null },
      },
    };
    const fetch = vi.fn(async (input, options = {}) => {
      if (String(input).includes("/api/business/tasks")) {
        return {
          ok: true,
          json: async () => ({
            8: { taskId: 8, taskTitle: "Review project", taskDateDue: null },
            9: { taskId: 9, taskTitle: "Prepare report", taskDateDue: "2026-11-05T12:00" },
          }),
        };
      }
      if (options.method === "PUT") {
        return { ok: true, json: async () => ({ updated: true }) };
      }
      return { ok: true, json: async () => ({ 3: schedule }) };
    });
    vi.stubGlobal("fetch", fetch);
    await loadEntitiesPage();

    expect(document.querySelector('[name="notificationTime"]').value).toBe("09:00");
    expect(document.querySelector('[name="notificationTimezone"]').value).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
    );
    expect(document.querySelectorAll('input[name="notificationDays"]:checked')).toHaveLength(7);
    expect(document.querySelector(".entity-form").hidden).toBe(true);

    document.querySelector(".record-actions button").click();
    await vi.waitFor(() =>
      expect(document.querySelector('[name="notificationTime"]').value).toBe("08:30"),
    );
    expect(document.querySelector(".entity-form").hidden).toBe(false);
    await vi.waitFor(() =>
      expect(document.querySelector(".scheduled-task-count").textContent)
        .toBe("1 task selected"),
    );
    const assignedTask = document.querySelector('input[name="taskIds"][value="8"]');
    const availableTask = document.querySelector('input[name="taskIds"][value="9"]');
    expect(assignedTask.checked).toBe(true);
    assignedTask.checked = false;
    assignedTask.dispatchEvent(new Event("change"));
    availableTask.checked = true;
    availableTask.dispatchEvent(new Event("change"));
    expect(document.querySelector(".scheduled-task-count").textContent).toBe("1 task selected");
    const wednesday = document.querySelector(
      'input[name="notificationDays"][value="WEDNESDAY"]',
    );
    wednesday.checked = false;
    document.querySelector('[name="notificationTime"]').value = "17:45";
    document.querySelector('[name="notificationTimezone"]').value = "America/Los_Angeles";
    document
      .querySelector(".entity-form")
      .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() =>
      expect(fetch).toHaveBeenCalledWith(
        "../api/business/schedules?id=3",
        expect.objectContaining({
          method: "PUT",
          body: JSON.stringify({
            notificationsEnabled: true,
            notificationDays: ["MONDAY"],
            notificationTime: "17:45",
            notificationTimezone: "America/Los_Angeles",
            taskIds: [9],
          }),
        }),
      ),
    );
    await vi.waitFor(() =>
      expect(document.querySelector(".entity-form").hidden).toBe(true),
    );
  });

  it("renders schedule detail user, tasks, and notification settings as cards", async () => {
    setPage({ entity: "schedules", mode: "detail", label: "Schedule" });
    history.replaceState(null, "", "/schedules/detail.html?id=3");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          notificationSettings: "DAILY",
          user: {
            userId: 3,
            firstName: "Jane",
            lastName: "Doe",
            userName: "jdoe",
            email: "jane@example.com",
            role: "USER",
          },
          tasks: {
            8: {
              taskId: 8,
              taskTitle: "Review",
              taskDescription: "Review the release",
              taskDateCreated: "2026-10-10T09:00",
              taskDateDue: "2026-10-16T12:00",
            },
          },
        }),
      }),
    );

    await loadEntitiesPage();

    const cards = document.querySelectorAll(
      "#results article.schedule-detail-card",
    );
    expect(cards).toHaveLength(3);
    expect(cards[0].querySelector("h2").textContent).toBe("Jane Doe (@jdoe)");
    expect(cards[0].textContent).toContain("jane@example.com · USER");
    expect(cards[1].querySelector("h2").textContent).toBe("Review");
    expect(cards[1].textContent).toContain('Due: "2026-10-16T12:00"');
    expect(cards[2].querySelector("h2").textContent).toBe(
      "Notification settings",
    );
    expect(cards[2].textContent).toContain("DAILY");
  });

  it("submits a search using the query string and renders matching records", async () => {
    setPage({ mode: "search" });
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [],
    });
    vi.stubGlobal("fetch", fetch);
    await loadEntitiesPage();

    document.querySelector("#search").value = "  plan work  ";
    document
      .querySelector("#search-form")
      .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await vi.waitFor(() =>
      expect(fetch).toHaveBeenCalledWith(
        "../api/business/tasks?search=plan+work",
      ),
    );
    await vi.waitFor(() =>
      expect(document.querySelector("#status").textContent).toBe(
        "No tasks records found.",
      ),
    );

    expect(window.location.search).toBe("?search=plan+work");
  });
});
