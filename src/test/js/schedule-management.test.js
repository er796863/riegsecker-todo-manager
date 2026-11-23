import { beforeEach, describe, expect, it, vi } from "vitest";

const scheduleManager = "../../main/webapp/schedules/manage.js";

describe("user schedule management", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetModules();
    history.replaceState(null, "", "/todo/schedules/index.html");
    document.body.innerHTML = `
      <header data-global-header></header>
      <output id="status"></output>
      <section id="schedule-summary" hidden>
        <ul id="schedule-summary-list"></ul>
      </section>
      <form id="schedule-form" hidden>
        <input id="schedule-name" />
        <select id="notification-settings">
          <option value="NONE">None</option>
          <option value="DAILY">Daily</option>
          <option value="WEEKLY">Weekly</option>
        </select>
        <fieldset id="schedule-task-options"><div id="schedule-tasks"></div></fieldset>
        <button id="schedule-submit" type="submit">Save schedule</button>
      </form>
    `;
  });

  it("loads shared task choices and saves only the current user's schedule fields", async () => {
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
      if (url.match(/\/api\/business\/schedules\?id=7$/)) {
        return { ok: true, json: async () => ({
          scheduleName: "Weekly plan",
          notificationSettings: "DAILY",
          tasks: { 8: { taskId: 8, taskTitle: "My task" } },
        }) };
      }
      if (url.endsWith("/api/business/schedules/available-tasks")) {
        return { ok: true, json: async () => [
          { taskId: 8, taskTitle: "My task", taskDateDue: null },
          { taskId: 9, taskTitle: "Shared task", taskDateDue: "2026-12-01T10:00:00" },
        ] };
      }
      if (url.endsWith("/api/business/schedules") && options.method === "PUT") {
        return { ok: true, json: async () => ({ updated: true }) };
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetch);

    await import(scheduleManager);

    const checkboxes = document.querySelectorAll('input[name="taskIds"]');
    expect(checkboxes).toHaveLength(2);
    expect(checkboxes[0].checked).toBe(true);
    expect(checkboxes[1].checked).toBe(false);
    expect(document.querySelector("#schedule-name").value).toBe("Weekly plan");
    expect(document.querySelector("#notification-settings").value).toBe("DAILY");
    expect(document.querySelector("#schedule-summary").hidden).toBe(false);
    expect(document.querySelector("#schedule-summary-list").textContent).toContain("Weekly plan");
    expect(document.querySelector("#schedule-summary-list").textContent).toContain("My task");

    document.querySelector("#schedule-name").value = "Work week";
    document.querySelector("#notification-settings").value = "WEEKLY";
    checkboxes[1].checked = true;
    document.querySelector("#schedule-form").dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true }),
    );
    await vi.waitFor(() =>
      expect(document.querySelector("#status").textContent).toBe("Schedule updated."),
    );

    const calls = fetch.mock.calls;
    expect(calls.some(([url]) => String(url).match(/\/api\/business\/schedules\?id=7$/))).toBe(true);
    expect(calls.some(([url]) => String(url).endsWith("/api/business/schedules/available-tasks"))).toBe(true);
    const updateCall = calls.find(([url, options]) =>
      String(url).endsWith("/api/business/schedules") && options?.method === "PUT");
    expect(updateCall[1].method).toBe("PUT");
    expect(JSON.parse(updateCall[1].body)).toEqual({
      scheduleName: "Work week",
      notificationSettings: "WEEKLY",
      taskIds: [8, 9],
    });
    expect(updateCall[1].body).not.toContain("userId");
    expect(document.querySelector("#status").textContent).toBe("Schedule updated.");
    expect(document.querySelector("#schedule-summary-list").textContent).toContain("Work week");
    expect(document.querySelector("#schedule-summary-list").textContent).toContain("Shared task");
  });
});
