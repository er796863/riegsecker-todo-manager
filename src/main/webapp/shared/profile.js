import { initializeAccountNavigation } from "./classes/AccountNavigation.js";

const formElement = document.querySelector("#profile-form");
const statusElement = document.querySelector("#account-status");
const usernameElement = document.querySelector("#profile-username");
const firstNameElement = document.querySelector("#profile-first-name");
const lastNameElement = document.querySelector("#profile-last-name");
const emailElement = document.querySelector("#profile-email");
const currentPasswordElement = document.querySelector("#profile-current-password");
const newPasswordElement = document.querySelector("#profile-new-password");
const submitElement = document.querySelector("#profile-submit");
const contextPath = new URL("../", import.meta.url).pathname.replace(/\/$/, "");

if (!(formElement instanceof HTMLFormElement)) {
	throw new Error("The profile page is missing required form elements.");
}
if (!(statusElement instanceof HTMLOutputElement)) {
	throw new Error("The profile page is missing required form elements.");
}
if (!(usernameElement instanceof HTMLInputElement)) {
	throw new Error("The profile page is missing required form elements.");
}
if (!(firstNameElement instanceof HTMLInputElement)) {
	throw new Error("The profile page is missing required form elements.");
}
if (!(lastNameElement instanceof HTMLInputElement)) {
	throw new Error("The profile page is missing required form elements.");
}
if (!(emailElement instanceof HTMLInputElement)) {
	throw new Error("The profile page is missing required form elements.");
}
if (!(currentPasswordElement instanceof HTMLInputElement)) {
	throw new Error("The profile page is missing required form elements.");
}
if (!(newPasswordElement instanceof HTMLInputElement)) {
	throw new Error("The profile page is missing required form elements.");
}
if (!(submitElement instanceof HTMLButtonElement)) {
	throw new Error("The profile page is missing required form elements.");
}

const form = formElement;
const status = statusElement;
const username = usernameElement;
const firstName = firstNameElement;
const lastName = lastNameElement;
const email = emailElement;
const currentPassword = currentPasswordElement;
const newPassword = newPasswordElement;
const submit = submitElement;

/**
 * @param {unknown} value
 * @returns {value is Record<string, unknown>}
 */
function isRecord(value) {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}

/**
 * @param {Record<string, unknown>} user
 * @param {string} field
 */
function requiredString(user, field) {
	const value = user[field];
	if (typeof value !== "string") {
		throw new Error(`The profile response is missing ${field}.`);
	}
	return value;
}

/**
 * @param {unknown} error
 */
function showError(error) {
	status.dataset.kind = "error";
	status.textContent = error instanceof Error ? error.message : "Unable to load your profile.";
}

async function loadProfile() {
	const user = await initializeAccountNavigation(contextPath);
	if (!user) {
		const next = `${window.location.pathname}${window.location.search}`;
		window.location.assign(`${contextPath}/account/login.html?next=${encodeURIComponent(
			next,
		)}`);
		return;
	}

	username.value = requiredString(user, "userName");
	firstName.value = requiredString(user, "firstName");
	lastName.value = requiredString(user, "lastName");
	email.value = requiredString(user, "email");
	form.hidden = false;
	status.textContent = "";
}

newPassword.addEventListener("input", () => {
	currentPassword.required = newPassword.value.length > 0;
});

form.addEventListener("submit", async (event) => {
	event.preventDefault();
	submit.disabled = true;
	status.removeAttribute("data-kind");

	try {
		const response = await fetch(`${contextPath}/api/auth/profile`, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			credentials: "same-origin",
			body: JSON.stringify({
				username: username.value,
				firstName: firstName.value,
				lastName: lastName.value,
				email: email.value,
				currentPassword: currentPassword.value,
				newPassword: newPassword.value,
			}),
		});
		const payload = await response.json();
		if (!response.ok) {
			throw new Error(payload.error || "Unable to save your profile.");
		}
		if (!isRecord(payload.user)) {
			throw new Error("The profile update returned invalid user details.");
		}

		username.value = requiredString(payload.user, "userName");
		const updatedFirstName = requiredString(payload.user, "firstName");
		const updatedLastName = requiredString(payload.user, "lastName");
		firstName.value = updatedFirstName;
		lastName.value = updatedLastName;
		email.value = requiredString(payload.user, "email");
		const greeting = document.querySelector("[data-user-greeting]");
		if (greeting) {
			greeting.textContent = `Signed in as ${updatedFirstName} ${updatedLastName}`;
		}
		currentPassword.value = "";
		newPassword.value = "";
		currentPassword.required = false;
		status.dataset.kind = "success";
		status.textContent = "Profile updated.";
	} catch (error) {
		showError(error);
	} finally {
		submit.disabled = false;
	}
});

try {
	await loadProfile();
} catch (error) {
	showError(error);
}
