import { initializeAccountNavigation } from "../shared/classes/AccountNavigation.js";

const form = document.querySelector("#task-form");
const status = document.querySelector("#status");
const results = document.querySelector("#task-results");
const title = document.querySelector("#task-title");
const description = document.querySelector("#task-description");
const dueDate = document.querySelector("#task-due-date");
const heading = document.querySelector("#task-form-heading");
const submit = document.querySelector("#task-submit");
const cancel = document.querySelector("#task-cancel");
const contextPath = new URL("../", import.meta.url).pathname.replace(/\/$/, "");
const endpoint = `${contextPath}/api/business/tasks`;

if (!(form instanceof HTMLFormElement)
	|| !(status instanceof HTMLOutputElement)
	|| !(results instanceof HTMLElement)
	|| !(title instanceof HTMLInputElement)
	|| !(description instanceof HTMLTextAreaElement)
	|| !(dueDate instanceof HTMLInputElement)
	|| !(heading instanceof HTMLHeadingElement)
	|| !(submit instanceof HTMLButtonElement)
	|| !(cancel instanceof HTMLButtonElement)) {
	throw new Error("The task page is missing required form elements.");
}

/**
 * @param {string} url
 * @param {RequestInit} [options]
 */
async function requestJson(url, options = {}) {
	const response = await fetch(url, {
		credentials: "same-origin",
		...options,
	});
	const payload = response.status === 204 ? null : await response.json();
	if (!response.ok) {
		throw new Error(payload?.error || `Request failed (${response.status}).`);
	}
	return payload;
}

function setStatus(message, kind = "info") {
	status.textContent = message;
	status.dataset.kind = kind;
}

function resetForm() {
	form.reset();
	delete form.dataset.taskId;
	heading.textContent = "Create task";
	submit.textContent = "Create task";
	cancel.hidden = true;
}

/**
 * @param {Record<string, unknown>} task
 */
function beginEdit(task) {
	form.dataset.taskId = String(task.taskId);
	title.value = String(task.taskTitle ?? "");
	description.value = String(task.taskDescription ?? "");
	dueDate.value = typeof task.taskDateDue === "string"
		? task.taskDateDue.slice(0, 16)
		: "";
	heading.textContent = "Edit task";
	submit.textContent = "Save task";
	cancel.hidden = false;
	form.hidden = false;
	title.focus();
}

/**
 * @param {Record<string, unknown>} task
 */
function renderTask(task) {
	const article = document.createElement("article");
	const taskTitle = document.createElement("h2");
	taskTitle.textContent = String(task.taskTitle ?? "Untitled task");
	article.append(taskTitle);

	const taskDescription = document.createElement("p");
	taskDescription.textContent = String(task.taskDescription ?? "No description");
	article.append(taskDescription);

	const due = document.createElement("p");
	due.textContent = `Due: ${typeof task.taskDateDue === "string"
		? task.taskDateDue.replace("T", " ")
		: "None"}`;
	article.append(due);

	const details = document.createElement("a");
	details.href = `./detail.html?id=${encodeURIComponent(String(task.taskId))}`;
	details.textContent = "View details";
	article.append(details);

	const actions = document.createElement("div");
	actions.className = "task-actions";
	const edit = document.createElement("button");
	edit.type = "button";
	edit.textContent = "Edit";
	edit.addEventListener("click", () => beginEdit(task));
	const remove = document.createElement("button");
	remove.type = "button";
	remove.textContent = "Delete";
	remove.addEventListener("click", () => deleteTask(task));
	actions.append(edit, remove);
	article.append(actions);
	return article;
}

/**
 * @param {Record<string, unknown>} task
 */
async function deleteTask(task) {
	if (!window.confirm(`Delete "${String(task.taskTitle ?? "this task")}"?`)) {
		return;
	}
	try {
		await requestJson(`${endpoint}?id=${encodeURIComponent(String(task.taskId))}`, {
			method: "DELETE",
		});
		resetForm();
		await loadTasks("Task deleted.");
	} catch (error) {
		setStatus(error instanceof Error ? error.message : "Unable to delete task.", "error");
	}
}

async function loadTasks(successMessage) {
	setStatus("Loading tasks...");
	const payload = await requestJson(endpoint);
	const records = Array.isArray(payload) ? payload : Object.values(payload);
	results.replaceChildren();
	if (records.length === 0) {
		setStatus(successMessage || "You have no tasks yet.");
		return;
	}
	for (const record of records) {
		if (typeof record === "object" && record !== null) {
			results.append(renderTask(record));
		}
	}
	setStatus(successMessage || `${records.length} task${records.length === 1 ? "" : "s"} found.`);
}

cancel.addEventListener("click", resetForm);
form.addEventListener("submit", async (event) => {
	event.preventDefault();
	submit.disabled = true;
	const taskId = form.dataset.taskId;
	try {
		await requestJson(taskId
			? `${endpoint}?id=${encodeURIComponent(taskId)}`
			: endpoint, {
			method: taskId ? "PUT" : "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				taskTitle: title.value,
				taskDescription: description.value,
				taskDateDue: dueDate.value || null,
			}),
		});
		resetForm();
		await loadTasks(taskId ? "Task updated." : "Task created.");
	} catch (error) {
		setStatus(error instanceof Error ? error.message : "Unable to save task.", "error");
	} finally {
		submit.disabled = false;
	}
});

try {
	const user = await initializeAccountNavigation(contextPath);
	if (!user) {
		const next = `${window.location.pathname}${window.location.search}`;
		window.location.assign(`${contextPath}/account/login.html?next=${encodeURIComponent(next)}`);
	} else {
		form.hidden = false;
		await loadTasks();
	}
} catch (error) {
	setStatus(error instanceof Error ? error.message : "Unable to load tasks.", "error");
}
