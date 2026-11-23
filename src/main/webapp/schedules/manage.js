import { initializeAccountNavigation } from "../shared/classes/AccountNavigation.js";

/**
 * @typedef {import("../shared/types/EntityRecords").ScheduleRecord} ScheduleRecord
 * @typedef {import("../shared/types/EntityRecords").TaskRecord} TaskRecord
 */

const form = document.querySelector("#schedule-form");
const status = document.querySelector("#status");
const name = document.querySelector("#schedule-name");
const settings = document.querySelector("#notification-settings");
const taskOptions = document.querySelector("#schedule-task-options");
const taskList = document.querySelector("#schedule-tasks");
const summary = document.querySelector("#schedule-summary");
const summaryList = document.querySelector("#schedule-summary-list");
const submit = document.querySelector("#schedule-submit");
const contextPath = new URL("../", import.meta.url).pathname.replace(/\/$/, "");
const schedulesEndpoint = `${contextPath}/api/business/schedules`;
/** @type {TaskRecord[]} */
let availableTaskRecords = [];

if (!(form instanceof HTMLFormElement)
	|| !(status instanceof HTMLOutputElement)
	|| !(name instanceof HTMLInputElement)
	|| !(settings instanceof HTMLSelectElement)
	|| !(taskOptions instanceof HTMLFieldSetElement)
	|| !(taskList instanceof HTMLElement)
	|| !(summary instanceof HTMLElement)
	|| !(summaryList instanceof HTMLUListElement)
	|| !(submit instanceof HTMLButtonElement)) {
	throw new Error("The schedule page is missing required form elements.");
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

/**
 * @param {Record<string, unknown>} task
 * @param {Set<string>} selectedTaskIds
 */
function renderTaskOption(task, selectedTaskIds) {
	const taskId = String(task.taskId);
	const label = document.createElement("label");
	label.className = "schedule-task-option";
	const checkbox = document.createElement("input");
	checkbox.type = "checkbox";
	checkbox.name = "taskIds";
	checkbox.value = taskId;
	checkbox.checked = selectedTaskIds.has(taskId);
	const title = typeof task.taskTitle === "string" ? task.taskTitle : "Untitled task";
	const dueDate = typeof task.taskDateDue === "string"
		? ` · due ${task.taskDateDue.replace("T", " ")}`
		: "";
	const text = document.createElement("span");
	text.textContent = `${title} (#${taskId})${dueDate}`;
	label.append(checkbox, text);
	return label;
}

/**
 * @param {string | null} value
 */
function formatDueDate(value) {
	if (!value) {
		return "No due date";
	}
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value.replace("T", " ");
	}
	return new Intl.DateTimeFormat(undefined, {
		dateStyle: "medium",
		timeStyle: "short",
	}).format(date);
}

/**
 * @param {Pick<ScheduleRecord, "scheduleName" | "notificationSettings" | "tasks">} schedule
 */
function renderScheduleSummary(schedule) {
	const entry = document.createElement("li");
	const article = document.createElement("article");
	article.className = "schedule-summary-card";

	const heading = document.createElement("h3");
	heading.textContent = schedule.scheduleName;
	article.append(heading);

	const notification = document.createElement("p");
	/** @type {Record<string, string>} */
	const notificationLabels = {
		NONE: "None",
		DAILY: "Daily",
		WEEKLY: "Weekly",
	};
	notification.textContent = `Notifications: ${
		notificationLabels[schedule.notificationSettings] ?? schedule.notificationSettings
	}`;
	article.append(notification);

	const tasksHeading = document.createElement("h4");
	tasksHeading.textContent = "Assigned tasks";
	article.append(tasksHeading);

	const tasks = Object.values(schedule.tasks ?? {});
	const assignedTasks = document.createElement("ul");
	if (tasks.length === 0) {
		const empty = document.createElement("li");
		empty.textContent = "No tasks assigned.";
		assignedTasks.append(empty);
	} else {
		for (const task of tasks) {
			const item = document.createElement("li");
			item.textContent = `${task.taskTitle} — due ${formatDueDate(task.taskDateDue)}`;
			assignedTasks.append(item);
		}
	}
	article.append(assignedTasks);
	entry.append(article);
	summaryList.replaceChildren(entry);
	summary.hidden = false;
}

async function initializeSchedule(userId) {
	setStatus("Loading schedule...");
	const params = new URLSearchParams({ id: String(userId) });
	const [schedule, availableTasks] = await Promise.all([
		requestJson(`${schedulesEndpoint}?${params}`),
		requestJson(`${schedulesEndpoint}/available-tasks`),
	]);
	if (!Array.isArray(availableTasks)) {
		throw new Error("The available task list is invalid.");
	}

	renderScheduleSummary(/** @type {ScheduleRecord} */ (schedule));
	name.value = String(schedule.scheduleName);
	settings.value = String(schedule.notificationSettings);
	const selectedTaskIds = new Set(Object.keys(schedule.tasks ?? {}));
	availableTaskRecords = availableTasks;
	taskList.replaceChildren();
	if (availableTasks.length === 0) {
		taskList.textContent = "No tasks are available.";
	} else {
		for (const task of availableTasks) {
			if (typeof task === "object" && task !== null) {
				taskList.append(renderTaskOption(task, selectedTaskIds));
			}
		}
	}
	form.hidden = false;
	setStatus("Schedule loaded.");
}

form.addEventListener("submit", async (event) => {
	event.preventDefault();
	submit.disabled = true;
	try {
		const taskIds = [...taskList.querySelectorAll('input[name="taskIds"]:checked')]
			.map((checkbox) => Number(checkbox.value));
		await requestJson(schedulesEndpoint, {
			method: "PUT",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				scheduleName: name.value,
				notificationSettings: settings.value,
				taskIds,
			}),
		});
		const selectedTaskIds = new Set(taskIds.map(String));
		const tasks = Object.fromEntries(
			availableTaskRecords
				.filter((task) => selectedTaskIds.has(String(task.taskId)))
				.map((task) => [String(task.taskId), task]),
		);
		renderScheduleSummary({
			scheduleName: name.value.trim(),
			notificationSettings: settings.value,
			tasks,
		});
		setStatus("Schedule updated.", "success");
	} catch (error) {
		setStatus(error instanceof Error ? error.message : "Unable to save schedule.", "error");
	} finally {
		submit.disabled = false;
	}
});

try {
	const user = await initializeAccountNavigation(contextPath);
	if (!user) {
		const next = `${window.location.pathname}${window.location.search}`;
		window.location.assign(`${contextPath}/account/login.html?next=${encodeURIComponent(next)}`);
	} else if (typeof user.userId !== "number" || !Number.isInteger(user.userId)) {
		throw new Error("The signed-in user ID is invalid.");
	} else {
		await initializeSchedule(user.userId);
	}
} catch (error) {
	setStatus(error instanceof Error ? error.message : "Unable to load schedule.", "error");
}
