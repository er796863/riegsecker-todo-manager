import { beforeEach, describe, expect, it, vi } from "vitest";

const taskManager = "../../main/webapp/tasks/manage.js";

describe("user task management", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetModules();
    history.replaceState(null, "", "/todo/tasks/index.html");
    document.body.innerHTML = `
      <header data-global-header></header>
      <output id="status"></output>
      <form id="task-form" hidden>
        <h2 id="task-form-heading"></h2>
        <input id="task-title" />
        <textarea id="task-description"></textarea>
        <input id="task-due-date" type="datetime-local" />
        <button id="task-submit" type="submit"></button>
        <button id="task-cancel" type="button" hidden>Cancel edit</button>
      </form>
      <section id="task-results"></section>
    `;
  });

  it("creates, edits, and deletes tasks through the session-scoped API", async () => {
    let tasks = {
      41: {
        taskId: 41,
        taskTitle: "Original task",
        taskDescription: "Original description",
        taskDateDue: null,
      },
    };
    const fetch = vi.fn(async (input, options = {}) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
          authenticated: true,
          user: { userId: 7, firstName: "Jane", lastName: "Doe", role: "USER" },
          }),
        };
      }
      if (url.endsWith("/api/notifications/check")) {
        return { ok: true, json: async () => ({ generated: false }) };
      }
      if (url.endsWith("/api/notifications")) {
        return { ok: true, json: async () => [] };
      }
      if (url.endsWith("/api/business/tasks")) {
        if (options.method === "POST") {
          tasks[42] = {
            taskId: 42,
            taskTitle: "New task",
            taskDescription: "Created by Jane",
          };
          return { ok: true, status: 201, json: async () => ({ taskId: 42 }) };
        }
        return { ok: true, json: async () => tasks };
      }
      if (url.includes("/api/business/tasks?id=") && options.method === "PUT") {
        tasks[41] = { taskId: 41, taskTitle: "Edited task", taskDescription: "Updated" };
        return { ok: true, json: async () => ({ taskId: 41 }) };
      }
      if (url.includes("/api/business/tasks?id=") && options.method === "DELETE") {
        delete tasks[41];
        return { ok: true, status: 204 };
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetch);
    vi.spyOn(window, "confirm").mockReturnValue(true);

    await import(taskManager);
    expect(document.querySelectorAll("#task-results article")).toHaveLength(1);

    document.querySelector("#task-title").value = "New task";
    document.querySelector("#task-description").value = "Created by Jane";
    document.querySelector("#task-form").dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true }),
    );
    await vi.waitFor(() => expect(document.querySelector("#status").textContent).toBe("Task created."));
    const createCall = fetch.mock.calls.find(([url, options]) =>
      String(url).endsWith("/api/business/tasks") && options?.method === "POST");
    expect(createCall[1].method).toBe("POST");
    expect(JSON.parse(createCall[1].body)).toEqual({
      taskTitle: "New task",
      taskDescription: "Created by Jane",
      taskDateDue: null,
    });

    document.querySelector("#task-results article button").click();
    expect(document.querySelector("#task-form-heading").textContent).toBe("Edit task");
    document.querySelector("#task-title").value = "Edited task";
    document.querySelector("#task-form").dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true }),
    );
    await vi.waitFor(() => expect(document.querySelector("#status").textContent).toBe("Task updated."));
    const updateCall = fetch.mock.calls.find(([url, options]) =>
      String(url).endsWith("/api/business/tasks?id=41") && options?.method === "PUT");
    expect(updateCall[1].method).toBe("PUT");

    document.querySelector("#task-results article button:last-of-type").click();
    const deleteCall = fetch.mock.calls.find(([url, options]) =>
      String(url).endsWith("/api/business/tasks?id=41") && options?.method === "DELETE");
    expect(deleteCall[1].method).toBe("DELETE");
    await vi.waitFor(() => expect(document.querySelector("#status").textContent).toBe("Task deleted."));
    expect(document.querySelectorAll("#task-results article")).toHaveLength(1);
  });
});
