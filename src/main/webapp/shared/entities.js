import { EntityPage } from "./classes/EntityPage.js";
import { initializeAccountNavigation } from "./classes/AccountNavigation.js";

const page = document.querySelector("[data-entity][data-mode]");

if (!(page instanceof HTMLElement)) {
	throw new TypeError("Unable to find entity page configuration.");
}

const contextPath = new URL("../", import.meta.url).pathname.replace(/\/$/, "");
try {
	const user = await initializeAccountNavigation(contextPath);
	if (!user) {
		const next = `${window.location.pathname}${window.location.search}`;
		window.location.assign(
			`${contextPath}/account/login.html?next=${encodeURIComponent(next)}`,
		);
	} else {
		await new EntityPage(page).initialize();
	}
} catch (error) {
	const status = document.querySelector("#status");
	if (status instanceof HTMLOutputElement) {
		status.textContent =
			error instanceof Error ? error.message : "Unable to check account session.";
		status.dataset.kind = "error";
	}
}
