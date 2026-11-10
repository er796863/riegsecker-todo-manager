import { beforeEach, describe, expect, it, vi } from "vitest";

const accountScript = "../../main/webapp/shared/account.js";
const accountNavigationScript =
  "../../main/webapp/shared/classes/AccountNavigation.js";

describe("account forms", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetModules();
    history.replaceState(null, "", "/todo/account/register.html");
    document.body.innerHTML = `
      <nav data-account-nav></nav>
      <form id="account-form" data-mode="register">
        <input name="username" value="jdoe" />
        <input name="firstName" value="Jane" />
        <input name="lastName" value="Doe" />
        <input name="email" value="jane@example.com" />
        <input name="password" value="a-long-password" />
        <button id="account-submit" type="submit">Create account</button>
      </form>
      <output id="account-status"></output>
    `;
  });

  it("renders the shared brand, page navigation, and sign-in options", async () => {
    document.body.innerHTML = `<header data-global-header></header>`;
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ authenticated: false }),
    }));

    const { initializeAccountNavigation } = await import(accountNavigationScript);
    await initializeAccountNavigation("/todo");

    expect(document.querySelector(".site-brand").textContent).toBe("ToDo Manager");
    expect([...document.querySelectorAll(".site-links a")].map((link) => link.textContent))
      .toEqual(["Tasks", "My profile", "My schedule"]);
    expect([...document.querySelectorAll(".account-actions a")].map((link) => link.textContent))
      .toEqual(["Sign in", "Create account"]);
    expect(document.querySelector(".account-actions a:last-child").href)
      .toMatch(/\/todo\/account\/register\.html$/);
  });

  it("adds all-record navigation only for administrators", async () => {
    document.body.innerHTML = `
      <header data-global-header></header>
      <a data-admin-only hidden href="/todo/admin/tasks.html">All tasks</a>
    `;
    vi.stubGlobal("fetch", vi.fn(async (input) => {
      if (String(input).endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            authenticated: true,
            user: { firstName: "Alex", lastName: "Rivera", role: "USER, ADMIN" },
          }),
        };
      }
      return {
        ok: true,
        json: async () => String(input).endsWith("/check") ? { generated: false } : [],
      };
    }));

    const { initializeAccountNavigation } = await import(accountNavigationScript);
    await initializeAccountNavigation("/todo");

    expect([...document.querySelectorAll(".site-links a")].map((link) => link.textContent))
      .toEqual(["Tasks", "My profile", "My schedule", "All tasks", "All users", "All schedules"]);
    expect([...document.querySelectorAll(".site-links a")].slice(3).map((link) => link.getAttribute("href")))
      .toEqual([
        "/todo/admin/tasks.html",
        "/todo/admin/users.html",
        "/todo/admin/schedules.html",
      ]);
    expect(document.querySelector("[data-admin-only]").hidden).toBe(false);
  });

  it("loads scheduled notifications and supports read and clear actions", async () => {
    document.body.innerHTML = `<header data-global-header></header>`;
    let notifications = [{
      notificationId: 12,
      scheduledDate: "2026-11-02",
      scheduledTime: "09:00",
      notificationTimezone: "America/Denver",
      read: false,
      additionalTaskCount: 0,
      tasks: [{ taskId: 31, taskTitle: "Review project", taskDateDue: null }],
    }];
    const fetch = vi.fn(async (input, options = {}) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            authenticated: true,
            user: { firstName: "Jane", lastName: "Doe", role: "USER" },
          }),
        };
      }
      if (url.endsWith("/api/notifications/check")) {
        return { ok: true, json: async () => ({ generated: false }) };
      }
      if (url.endsWith("/api/notifications") && options.method === "DELETE") {
        notifications = [];
        return { ok: true, json: async () => ({ deletedCount: 1 }) };
      }
      if (url.includes("/api/notifications/read?id=")) {
        notifications = notifications.map((notification) => ({
          ...notification,
          read: true,
        }));
        return { ok: true, json: async () => ({ read: true }) };
      }
      if (url.includes("/api/notifications?id=")) {
        notifications = [];
        return { ok: true, json: async () => ({ deleted: true }) };
      }
      if (url.endsWith("/api/notifications")) {
        return { ok: true, json: async () => notifications };
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetch);

    const { initializeAccountNavigation } = await import(accountNavigationScript);
    await initializeAccountNavigation("/todo");

    expect(fetch).toHaveBeenCalledWith(
      "/todo/api/notifications/check",
      expect.objectContaining({ method: "POST" }),
    );
    await vi.waitFor(() =>
      expect(document.querySelector(".notification-count").textContent).toBe("1"),
    );
    document.querySelector(".notification-toggle").click();
    expect(document.querySelector(".notification-panel").hidden).toBe(false);
    expect(document.querySelector(".notification-item").textContent).toContain("Review project");

    document.querySelector(".notification-item button").click();
    await vi.waitFor(() =>
      expect(document.querySelector(".notification-read-label").textContent).toBe("Read"),
    );
    expect(document.querySelector(".notification-count").hidden).toBe(true);

    document.querySelector(".notification-clear").click();
    await vi.waitFor(() =>
      expect(document.querySelector(".notification-empty").textContent)
        .toBe("You have no notifications."),
    );
  });

  it("posts registration details and displays server validation errors as text", async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ authenticated: false }),
      })
      .mockResolvedValueOnce({
        ok: false,
        status: 409,
        json: async () => ({ error: "<b>Username is already in use</b>" }),
      });
    vi.stubGlobal("fetch", fetch);

    await import(accountScript);
    document
      .querySelector("#account-form")
      .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
    await vi.waitFor(() =>
      expect(document.querySelector("#account-status").textContent).toBe(
        "<b>Username is already in use</b>",
      ),
    );

    expect(fetch.mock.calls[1][0]).toMatch(/\/api\/auth\/register$/);
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({
      username: "jdoe",
      firstName: "Jane",
      lastName: "Doe",
      email: "jane@example.com",
      password: "a-long-password",
    });
    expect(document.querySelector("#account-status b")).toBeNull();
  });
});
