/**
 * @param {string} contextPath
 * @returns {Promise<Record<string, unknown> | null>}
 */
export async function initializeAccountNavigation(contextPath) {
	const header = document.querySelector("[data-global-header]");
	let navigation = document.querySelector("[data-account-nav]");
	if (header) {
		const inner = document.createElement("div");
		inner.className = "site-header-inner";

		const brand = document.createElement("a");
		brand.className = "site-brand";
		brand.href = `${contextPath}/`;
		brand.textContent = "ToDo Manager";
		inner.append(brand);

		const links = document.createElement("nav");
		links.className = "site-links";
		links.setAttribute("aria-label", "Main navigation");
		for (const [label, path] of [
			["Tasks", "tasks/index.html"],
			["My profile", "users/index.html"],
			["My schedule", "schedules/index.html"],
		]) {
			const link = document.createElement("a");
			link.href = `${contextPath}/${path}`;
			link.textContent = label;
			links.append(link);
		}
		inner.append(links);

		if (!navigation) {
			navigation = document.createElement("nav");
			navigation.setAttribute("aria-label", "Account");
		}
		navigation.classList.add("account-actions");
		navigation.setAttribute("data-account-nav", "");
		inner.append(navigation);
		header.classList.add("site-header");
		header.replaceChildren(inner);
	}

	const response = await fetch(`${contextPath}/api/auth/session`, {
		credentials: "same-origin",
	});
	const payload = await response.json();
	if (!response.ok) {
		throw new Error(payload.error || "Unable to check account session.");
	}

	if (!navigation) {
		setAdminNavigation(payload.user);
		return payload.authenticated ? payload.user : null;
	}

	navigation.replaceChildren();
	if (!payload.authenticated) {
		setAdminNavigation(null);
		const login = document.createElement("a");
		login.href = `${contextPath}/account/login.html`;
		login.textContent = "Sign in";
		const register = document.createElement("a");
		register.href = `${contextPath}/account/register.html`;
		register.textContent = "Create account";
		navigation.append(login, register);
		return null;
	}

	if (isAdmin(payload.user)) {
		for (const [label, path] of [
			["All tasks", "admin/tasks.html"],
			["All users", "admin/users.html"],
			["All schedules", "admin/schedules.html"],
		]) {
			const link = document.createElement("a");
			link.href = `${contextPath}/${path}`;
			link.textContent = label;
			document.querySelector(".site-links")?.append(link);
		}
	}
	setAdminNavigation(payload.user);

	const greeting = document.createElement("span");
	greeting.textContent = `Signed in as ${payload.user.firstName} ${payload.user.lastName}`;
	const logout = document.createElement("button");
	logout.type = "button";
	logout.textContent = "Sign out";
	logout.addEventListener("click", async () => {
		logout.disabled = true;
		try {
			const logoutResponse = await fetch(`${contextPath}/api/auth/logout`, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				credentials: "same-origin",
				body: "{}",
			});
			if (!logoutResponse.ok) {
				throw new Error("Unable to sign out. Please try again.");
			}
			window.location.assign(`${contextPath}/account/login.html`);
		} catch (error) {
			logout.disabled = false;
			const status = document.querySelector("#account-status, #status");
			if (status) {
				status.textContent =
					error instanceof Error ? error.message : "Unable to sign out.";
			}
		}
	});
	navigation.append(greeting, logout);
	return payload.user;
}

/**
 * @param {Record<string, unknown> | null} user
 * @returns {boolean}
 */
function isAdmin(user) {
	return typeof user?.role === "string"
		&& user.role.split(",").some((role) => role.trim().toUpperCase() === "ADMIN");
}

/**
 * @param {Record<string, unknown> | null} user
 */
function setAdminNavigation(user) {
	const visible = isAdmin(user);
	document.querySelectorAll("[data-admin-only]").forEach((element) => {
		if (element instanceof HTMLElement) {
			element.hidden = !visible;
		}
	});
}
