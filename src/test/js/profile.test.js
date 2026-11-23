import { beforeEach, describe, expect, it, vi } from "vitest";

const profileScript = "../../main/webapp/shared/profile.js";

describe("profile editing", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetModules();
    history.replaceState(null, "", "/todo/users/index.html");
    document.body.innerHTML = `
      <header data-global-header></header>
      <output id="account-status">Loading profile...</output>
      <form id="profile-form" hidden>
        <input id="profile-username" name="username" />
        <input id="profile-first-name" name="firstName" />
        <input id="profile-last-name" name="lastName" />
        <input id="profile-email" name="email" />
        <input id="profile-current-password" name="currentPassword" type="password" />
        <input id="profile-new-password" name="newPassword" type="password" />
        <button id="profile-submit" type="submit">Save changes</button>
      </form>
    `;
  });

  it("loads the signed-in profile and saves only editable profile fields", async () => {
    const fetch = vi.fn(async (input) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return { ok: true, json: async () => ({
          authenticated: true,
          user: {
            userId: 7,
            userName: "jdoe",
            firstName: "Jane",
            lastName: "Doe",
            email: "jane@example.com",
            role: "USER",
          },
        }) };
      }
      if (url.endsWith("/api/notifications/check")) {
        return { ok: true, json: async () => ({ generated: false }) };
      }
      if (url.endsWith("/api/notifications")) {
        return { ok: true, json: async () => [] };
      }
      if (url.endsWith("/api/auth/profile")) {
        return { ok: true, json: async () => ({
          user: {
            userId: 7,
            userName: "janet",
            firstName: "Janet",
            lastName: "Doe",
            email: "janet@example.com",
            role: "USER",
          },
        }) };
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetch);

    await import(profileScript);

    expect(document.querySelector("#profile-username").value).toBe("jdoe");
    expect(document.querySelector("#profile-first-name").value).toBe("Jane");
    expect(document.querySelector("#profile-last-name").value).toBe("Doe");
    expect(document.querySelector("#profile-email").value).toBe("jane@example.com");
    expect(document.querySelector("#profile-form").hidden).toBe(false);

    document.querySelector("#profile-username").value = "janet";
    document.querySelector("#profile-first-name").value = "Janet";
    document.querySelector("#profile-email").value = "janet@example.com";
    document.querySelector("#profile-current-password").value = "previous-password";
    document.querySelector("#profile-new-password").value = "a-new-long-password";
    document.querySelector("#profile-new-password").dispatchEvent(new Event("input"));
    expect(document.querySelector("#profile-current-password").required).toBe(true);
    document.querySelector("#profile-form").dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true }),
    );

    await vi.waitFor(() =>
      expect(document.querySelector("#account-status").textContent).toBe("Profile updated."),
    );
    const updateCall = fetch.mock.calls.find(([url]) => String(url).endsWith("/api/auth/profile"));
    expect(updateCall[0]).toMatch(/\/api\/auth\/profile$/);
    expect(JSON.parse(updateCall[1].body)).toEqual({
      username: "janet",
      firstName: "Janet",
      lastName: "Doe",
      email: "janet@example.com",
      currentPassword: "previous-password",
      newPassword: "a-new-long-password",
    });
    expect(updateCall[1].body).not.toContain("userId");
    expect(document.querySelector("[data-user-greeting]").textContent)
      .toBe("Signed in as Janet Doe");
    expect(document.querySelector("#profile-current-password").value).toBe("");
    expect(document.querySelector("#profile-new-password").value).toBe("");
  });
});
