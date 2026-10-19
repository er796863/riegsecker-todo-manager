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
