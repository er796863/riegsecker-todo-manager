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
          user: { firstName: "Jane", lastName: "Doe" },
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
