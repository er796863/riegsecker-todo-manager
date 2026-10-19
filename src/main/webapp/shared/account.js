import { initializeAccountNavigation } from "./classes/AccountNavigation.js";

const contextPath = new URL("../", import.meta.url).pathname.replace(/\/$/, "");
const form = document.querySelector("#account-form");
const status = document.querySelector("#account-status");
const submit = document.querySelector("#account-submit");

if (!(form instanceof HTMLFormElement) || !(status instanceof HTMLOutputElement)
	|| !(submit instanceof HTMLButtonElement)) {
	throw new TypeError("Account page is missing its form or status output.");
}

form.addEventListener("submit", async (event) => {
	event.preventDefault();
	status.textContent = "";
	submit.disabled = true;
	const values = Object.fromEntries(new FormData(form).entries());
	const mode = form.dataset.mode;

	try {
		const response = await fetch(`${contextPath}/api/auth/${mode}`, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			credentials: "same-origin",
			body: JSON.stringify(values),
		});
		const payload = await response.json();
		if (!response.ok) {
			throw new Error(payload.error || `Request failed (${response.status}).`);
		}

		const requestedNext = new URLSearchParams(window.location.search).get("next");
		const safeNext = requestedNext
			&& requestedNext.startsWith(`${contextPath}/`)
			&& !requestedNext.startsWith("//")
			? requestedNext
			: `${contextPath}/`;
		window.location.assign(safeNext);
	} catch (error) {
		status.textContent =
			error instanceof Error ? error.message : "Unable to process account request.";
		submit.disabled = false;
	}
});

initializeAccountNavigation(contextPath).catch((error) => {
	status.textContent =
		error instanceof Error ? error.message : "Unable to check account session.";
});
