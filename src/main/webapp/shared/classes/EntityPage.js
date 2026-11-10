import { EntityRenderer } from "./EntityRenderer.js";

/**
 * @typedef {import("../types/EntityRecords").EntityName} EntityName
 * @typedef {import("../types/EntityRecords").EntityRecord} EntityRecord
 * @typedef {import("../types/EntityRecords").EntityRecordCollection} EntityRecordCollection
 */

export class EntityPage {
	/**
	 * @param {HTMLElement} page
	 */
	constructor(page) {
		const entity = page.dataset.entity;
		const mode = page.dataset.mode;
		const label = page.dataset.label;

		if (!isEntityName(entity) || !mode || !label) {
			throw new Error(
				"Entity page configuration is incomplete or invalid.",
			);
		}

		this.entity = entity;
		this.mode = mode;
		this.label = label;
		this.scope = page.dataset.scope ?? "mine";

		if (this.scope !== "mine" && this.scope !== "all") {
			throw new Error("Entity page scope must be mine or all.");
		}

		this.detailBase = page.dataset.detailBase ?? "";
		this.endpoint = `../api/business/${entity}`;
		this.canManage = false;
		this.currentUserId = null;
		this.editingId = null;
		this.currentSearch = null;
		this.managementForm = null;
		this.managementStatus = null;

		const statusOutput = document.querySelector("#status");
		const results = document.querySelector("#results");

		if (
			!(statusOutput instanceof HTMLOutputElement) ||
			!(results instanceof HTMLElement)
		) {
			throw new TypeError(
				"Entity page is missing its status or results element.",
			);
		}

		this.renderer = new EntityRenderer({
			entity,
			label,
			scope: this.scope,
			detailBase: this.detailBase,
			statusOutput,
			results,
			onEdit: (record) => this.startEdit(record).catch((error) => {
				this.renderer.setStatus(this.errorMessage(error), "error");
			}),
			onDelete: (record) => this.deleteRecord(record).catch((error) => {
				this.renderer.setStatus(this.errorMessage(error), "error");
			}),
		});
	}

	/**
	 * @param {Record<string, unknown> | null} user
	 * @returns {Promise<void>}
	 */
	async initialize(user) {
		this.currentUserId =
			typeof user?.userId === "number" ? user.userId : null;

		const admin = isAdmin(user);

		this.canManage =
			this.mode !== "detail" &&
			(this.entity !== "users" || (this.scope === "all" && admin)) &&
			(this.scope !== "all" || admin);

		this.renderer.canManage = this.canManage;
		this.renderer.currentUserId = this.currentUserId;

		if (this.canManage) {
			try {
				await this.initializeManagement();
			} catch (error) {
				const message = this.errorMessage(error);
				this.renderer.setStatus(message, "error");

				if (this.managementStatus) {
					this.managementStatus.textContent = message;
					this.managementStatus.dataset.kind = "error";
				}
			}
		}

		if (this.mode === "list") {
			try {
				await this.loadList(null);
			} catch (error) {
				this.renderer.setStatus(this.errorMessage(error), "error");
			}
		} else if (this.mode === "detail") {
			try {
				await this.loadDetails();
			} catch (error) {
				this.renderer.setStatus(this.errorMessage(error), "error");
			}
		} else if (this.mode === "search") {
			await this.initializeSearch();
		}
	}

	/**
	 * @param {string} url
	 * @returns {Promise<unknown>}
	 */
	async getJson(url) {
		const response = await fetch(url);
		const data = await response.json();

		if (!response.ok) {
			throw new Error(
				data.error || `Request failed with status ${response.status}`,
			);
		}

		return data;
	}

	/**
	 * @param {string | null} search
	 * @returns {Promise<void>}
	 */
	async loadList(search) {
		this.currentSearch = search;
		this.renderer.setStatus(`Loading ${this.label.toLowerCase()}...`);

		const params = new URLSearchParams();
		if (search !== null) {
			params.set("search", search);
		}

		if (this.scope === "all") {
			params.set("scope", "all");
		}

		const suffix = params.size > 0 ? `?${params}` : "";

		const records = /** @type {EntityRecordCollection} */ (
			await this.getJson(`${this.endpoint}${suffix}`)
		);

		this.renderer.renderList(records);
	}

	async initializeManagement() {
		const heading = document.createElement("h2");
		heading.textContent = `Manage ${this.label.toLowerCase()}`;

		const section = document.createElement("section");
		section.className = "entity-management";

		const status = document.createElement("output");
		this.managementStatus = status;
		section.append(heading, status);

		const form = document.createElement("form");
		form.className = "entity-form";
		this.managementForm = form;

		if (this.entity === "tasks") {
			this.addField(form, "Task title", "taskTitle", "text", true, 150);
			this.addTextarea(form, "Description", "taskDescription", 1000);
			this.addField(form, "Due date", "taskDateDue", "datetime-local", false);
		} else if (this.entity === "schedules") {
			this.addNotificationSection(form);
			this.addScheduledTasks(form);
		} else {
			this.addField(form, "Username", "username", "text", true, 50);
			this.addField(form, "First name", "firstName", "text", true, 50);
			this.addField(form, "Last name", "lastName", "text", true, 50);
			this.addField(form, "Email", "email", "email", true, 254);
			this.addField(form, "Password", "password", "password", true, 72);
			this.addSelect(form, "Role", "role", [
				["USER", "User"],
				["ADMIN", "Administrator"],
			]);

			const password = form.elements.namedItem("password");
			if (password instanceof HTMLInputElement) {
				password.minLength = 12;
				password.autocomplete = "new-password";
			}
		}

		const actions = document.createElement("div");
		actions.className = "form-actions";

		const submit = document.createElement("button");
		submit.type = "submit";
		submit.textContent = `Create ${this.label.replace(/s$/, "")}`;

		const cancel = document.createElement("button");
		cancel.type = "button";
		cancel.textContent = "Cancel edit";
		cancel.hidden = true;
		cancel.addEventListener("click", () => this.resetManagementForm());
		actions.append(submit, cancel);

		form.append(actions);
		form.addEventListener("submit", (event) => {
			event.preventDefault();
			void this.saveRecord().catch((error) => {
				this.renderer.setStatus(this.errorMessage(error), "error");
			});
		});

		form.hidden = this.entity === "schedules";
		section.append(form);

		document.querySelector("#results")?.before(section);
		if (this.entity === "schedules" && this.currentUserId !== null) {
			await this.loadScheduleTasks(this.currentUserId);
		}
	}

	/**
	 * @param {HTMLElement} form
	 */
	addNotificationSection(form) {
		const section = document.createElement("fieldset");
		section.className = "notification-settings";

		const legend = document.createElement("legend");
		legend.textContent = "Notification settings";
		section.append(legend);

		const enabledField = document.createElement("div");
		enabledField.className = "form-field notification-enabled";

		const enabled = document.createElement("input");
		enabled.type = "checkbox";
		enabled.name = "notificationsEnabled";
		enabled.id = "entity-notificationsEnabled";
		enabled.checked = true;

		const enabledLabel = document.createElement("label");
		enabledLabel.htmlFor = enabled.id;
		enabledLabel.textContent = "Enable notification preferences";
		enabledField.append(enabled, enabledLabel);

		section.append(enabledField);

		const daysField = document.createElement("fieldset");
		daysField.className = "notification-days";

		const daysLegend = document.createElement("legend");
		daysLegend.textContent = "Days of the week";
		daysField.append(daysLegend);

		for (const [value, labelText] of [
			["MONDAY", "Monday"],
			["TUESDAY", "Tuesday"],
			["WEDNESDAY", "Wednesday"],
			["THURSDAY", "Thursday"],
			["FRIDAY", "Friday"],
			["SATURDAY", "Saturday"],
			["SUNDAY", "Sunday"],
		]) {
			const label = document.createElement("label");

			const checkbox = document.createElement("input");
			checkbox.type = "checkbox";
			checkbox.name = "notificationDays";
			checkbox.value = value;
			checkbox.checked = true;
			label.append(checkbox, document.createTextNode(labelText));

			daysField.append(label);
		}

		section.append(daysField);

		this.addField(section, "Send time", "notificationTime", "time", true);

		const timeInput = section.querySelector('[name="notificationTime"]');
		if (timeInput instanceof HTMLInputElement) {
			timeInput.value = "09:00";
		}

		this.addField(section, "Time zone", "notificationTimezone", "text", true, 64);

		const timezoneInput = section.querySelector('[name="notificationTimezone"]');
		if (timezoneInput instanceof HTMLInputElement) {
			timezoneInput.value =
				Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
		}

		const help = document.createElement("small");
		help.textContent = "Saved for the selected time zone; no notifications are sent yet.";
		section.append(help);

		enabled.addEventListener("change", () => {
			daysField.disabled = !enabled.checked;
			for (const name of ["notificationTime", "notificationTimezone"]) {
				const input = section.querySelector(`[name="${name}"]`);
				if (input instanceof HTMLInputElement) {
					input.disabled = !enabled.checked;
				}
			}
		});

		form.append(section);
	}

	/**
	 * @param {HTMLElement} form
	 */
	addScheduledTasks(form) {
		const section = document.createElement("fieldset");
		section.className = "scheduled-task-picker";

		const legend = document.createElement("legend");
		legend.textContent = "Tasks to include";

		const help = document.createElement("small");
		help.textContent = "Choose the tasks you want included in your scheduled notification.";
		help.id = "entity-taskIds-help";

		const controls = document.createElement("div");
		controls.className = "scheduled-task-controls";

		const selectAll = document.createElement("button");
		selectAll.type = "button";
		selectAll.textContent = "Select all";

		selectAll.addEventListener("click", () => {
			for (const checkbox of this.scheduledTaskCheckboxes()) {
				checkbox.checked = true;
			}
			this.updateScheduledTaskCount();
		});

		const clear = document.createElement("button");
		clear.type = "button";
		clear.textContent = "Clear selection";

		clear.addEventListener("click", () => {
			for (const checkbox of this.scheduledTaskCheckboxes()) {
				checkbox.checked = false;
			}
			this.updateScheduledTaskCount();
		});

		controls.append(selectAll, clear);

		const list = document.createElement("div");
		list.className = "scheduled-task-list";
		list.setAttribute("aria-describedby", help.id);

		const count = document.createElement("output");
		count.className = "scheduled-task-count";
		count.setAttribute("aria-live", "polite");
		count.textContent = "No tasks selected";
		section.append(legend, help, controls, list, count);

		form.append(section);
	}

	/**
	 * @returns {NodeListOf<HTMLInputElement>}
	 */
	scheduledTaskCheckboxes() {
		return this.managementForm?.querySelectorAll('input[name="taskIds"]')
			?? document.createDocumentFragment().querySelectorAll("input");
	}

	updateScheduledTaskCount() {
		const checkboxes = this.scheduledTaskCheckboxes();
		const selectedCount = Array.from(checkboxes).filter((checkbox) => checkbox.checked).length;
		const output = this.managementForm?.querySelector(".scheduled-task-count");

		if (output) {
			output.textContent = selectedCount === 0
				? "No tasks selected"
				: `${selectedCount} task${selectedCount === 1 ? "" : "s"} selected`;
		}
	}

	/**
	 * @param {HTMLElement} form
	 * @param {string} labelText
	 * @param {string} name
	 * @param {string} type
	 * @param {boolean} required
	 * @param {number} [maxLength]
	 */
	addField(form, labelText, name, type, required, maxLength) {
		const field = document.createElement("div");
		field.className = "form-field";

		const label = document.createElement("label");
		label.textContent = labelText;
		label.htmlFor = `entity-${name}`;

		const input = document.createElement("input");
		input.id = label.htmlFor;
		input.name = name;
		input.type = type;
		input.required = required;

		if (maxLength) {
			input.maxLength = maxLength;
		}

		field.append(label, input);
		form.append(field);
	}

	/**
	 * @param {HTMLElement} form
	 * @param {string} labelText
	 * @param {string} name
	 * @param {number} maxLength
	 */
	addTextarea(form, labelText, name, maxLength) {
		const field = document.createElement("div");
		field.className = "form-field";

		const label = document.createElement("label");
		label.textContent = labelText;
		label.htmlFor = `entity-${name}`;

		const input = document.createElement("textarea");
		input.id = label.htmlFor;
		input.name = name;
		input.maxLength = maxLength;
		input.rows = 3;

		field.append(label, input);
		form.append(field);
	}

	/**
	 * @param {HTMLElement} form
	 * @param {string} labelText
	 * @param {string} name
	 * @param {Array<[string, string]>} choices
	 * @param {boolean} [multiple]
	 */
	addSelect(form, labelText, name, choices, multiple = false) {
		const field = document.createElement("div");
		field.className = "form-field";

		const label = document.createElement("label");
		label.textContent = labelText;
		label.htmlFor = `entity-${name}`;

		const select = document.createElement("select");
		select.id = label.htmlFor;
		select.name = name;
		select.multiple = multiple;

		for (const [value, text] of choices) {
			const option = document.createElement("option");
			option.value = value;
			option.textContent = text;
			select.append(option);
		}

		field.append(label, select);
		form.append(field);
	}

	/**
	 * @param {EntityRecord} record
	 * @returns {Promise<void>}
	 */
	async startEdit(record) {
		const form = this.managementForm;
		if (!form) {
			return;
		}

		form.hidden = false;
		this.editingId = this.recordId(record);

		if (this.entity === "tasks") {
			const task = /** @type {import("../types/EntityRecords").TaskRecord} */ (record);
			this.setFieldValue("taskTitle", task.taskTitle);
			this.setFieldValue("taskDescription", task.taskDescription ?? "");
			this.setFieldValue("taskDateDue", task.taskDateDue ?? "");
		} else if (this.entity === "users") {
			const user = /** @type {import("../types/EntityRecords").UserRecord} */ (record);
			this.setFieldValue("username", user.userName);
			this.setFieldValue("firstName", user.firstName);
			this.setFieldValue("lastName", user.lastName);
			this.setFieldValue("email", user.email);
			this.setFieldValue("password", "");
			this.setFieldValue("role", user.role.split(",")[0].trim());

			const password = form.elements.namedItem("password");
			if (password instanceof HTMLInputElement) {
				password.required = false;
				password.placeholder = "Leave blank to keep the current password";
			}
		} else {
			const schedule = /** @type {import("../types/EntityRecords").ScheduleRecord} */ (record);
			this.setFieldValue("notificationsEnabled", schedule.notificationsEnabled);

			const notificationsEnabled = form.elements.namedItem("notificationsEnabled");
			if (notificationsEnabled instanceof HTMLInputElement) {
				notificationsEnabled.dispatchEvent(new Event("change"));
			}

			this.setFieldValue("notificationDays", schedule.notificationDays);
			this.setFieldValue("notificationTime", schedule.notificationTime);
			this.setFieldValue("notificationTimezone", schedule.notificationTimezone);

			await this.loadScheduleTasks(
				schedule.user.userId,
				Object.keys(schedule.tasks),
			);
		}

		const submit = form.querySelector('button[type="submit"]');
		const cancel = form.querySelector('button[type="button"]');

		if (submit instanceof HTMLButtonElement) {
			submit.textContent = `Save ${this.label.replace(/s$/, "")}`;
		}

		if (cancel instanceof HTMLButtonElement) {
			cancel.hidden = false;
		}

		form.scrollIntoView({ behavior: "smooth", block: "start" });
	}

	/**
	 * @param {number} userId
	 * @param {string[]} [selectedIds]
	 * @returns {Promise<void>}
	 */
	async loadScheduleTasks(userId, selectedIds = []) {
		const params = new URLSearchParams();
		if (this.scope === "all") {
			params.set("scope", "all");
			params.set("userId", String(userId));
		}

		const suffix = params.size > 0 ? `?${params}` : "";
		const records = /** @type {EntityRecordCollection} */ (
			await this.getJson(`../api/business/tasks${suffix}`)
		);

		const list = this.managementForm?.querySelector(".scheduled-task-list");
		if (!list) {
			return;
		}
		list.replaceChildren();

		const tasks = Array.isArray(records) ? records : Object.values(records);
		if (tasks.length === 0) {
			const empty = document.createElement("p");
			empty.className = "scheduled-task-empty";
			empty.textContent = "No tasks are available for this schedule yet.";
			list.append(empty);
			this.updateScheduledTaskCount();
			return;
		}

		for (const value of tasks) {
			const task = /** @type {import("../types/EntityRecords").TaskRecord} */ (value);

			const label = document.createElement("label");
			label.className = "scheduled-task-option";

			const checkbox = document.createElement("input");
			checkbox.type = "checkbox";
			checkbox.name = "taskIds";
			checkbox.value = String(task.taskId);
			checkbox.checked = selectedIds.includes(String(task.taskId));
			checkbox.addEventListener("change", () => this.updateScheduledTaskCount());

			const title = document.createElement("span");
			title.className = "scheduled-task-title";
			title.textContent = task.taskTitle;
			label.append(checkbox, title);

			if (task.taskDateDue) {
				const dueDate = document.createElement("small");
				dueDate.className = "scheduled-task-due-date";
				dueDate.textContent = `Due ${task.taskDateDue.replace("T", " ")}`;
				label.append(dueDate);
			}

			list.append(label);
		}

		this.updateScheduledTaskCount();
	}

	/** @returns {Promise<void>} */
	async saveRecord() {
		const form = this.managementForm;
		if (!form) {
			return;
		}

		const values = Object.fromEntries(new FormData(form).entries()); /** @type {Record<string, unknown>} */

		let body;
		if (this.entity === "tasks") {
			body = {
				taskTitle: values.taskTitle,
				taskDescription: values.taskDescription || null,
				taskDateDue: values.taskDateDue || null,
			};
		} else if (this.entity === "users") {
			body = {
				username: values.username,
				firstName: values.firstName,
				lastName: values.lastName,
				email: values.email,
				password: values.password || null,
				role: values.role,
			};
		} else {
			const taskIds = Array.from(
				form.querySelectorAll('input[name="taskIds"]:checked'),
				(input) => input instanceof HTMLInputElement ? Number(input.value) : 0,
			);

			const notificationsEnabled = form.elements.namedItem("notificationsEnabled");
			const notificationDays = Array.from(
				form.querySelectorAll('input[name="notificationDays"]:checked'),
				(input) => input instanceof HTMLInputElement ? input.value : "",
			);

			const notificationTime = form.elements.namedItem("notificationTime");
			const notificationTimezone = form.elements.namedItem("notificationTimezone");

			body = {
				notificationsEnabled: notificationsEnabled instanceof HTMLInputElement
					&& notificationsEnabled.checked,
				notificationDays,
				notificationTime: notificationTime instanceof HTMLInputElement
					? notificationTime.value
					: "",
				notificationTimezone: notificationTimezone instanceof HTMLInputElement
					? notificationTimezone.value
					: "",
				taskIds,
			};
		}

		const params = new URLSearchParams();
		if (this.scope === "all") {
			params.set("scope", "all");
		}

		if (this.editingId !== null) {
			params.set("id", String(this.editingId));
		}

		const suffix = params.size > 0 ? `?${params}` : "";
		const response = await fetch(`${this.endpoint}${suffix}`, {
			method: this.editingId === null ? "POST" : "PUT",
			headers: { "Content-Type": "application/json" },
			credentials: "same-origin",
			body: JSON.stringify(body),
		});

		const payload = await response.json();
		if (!response.ok) {
			this.renderer.setStatus(
				payload.error || `Request failed with status ${response.status}`,
				"error",
			);

			return;
		}

		this.resetManagementForm();
		await this.loadList(this.currentSearch);
	}

	/**
	 * @param {EntityRecord} record
	 * @returns {Promise<void>}
	 */
	async deleteRecord(record) {
		const id = this.recordId(record);
		const warning = this.entity === "users"
			? " Their tasks and schedule will also be deleted."
			: "";

		if (!window.confirm(
			`Delete this ${this.label.replace(/s$/, "").toLowerCase()}?${warning}`,
		)) {
			return;
		}

		const params = new URLSearchParams({ id: String(id) });
		if (this.scope === "all") {
			params.set("scope", "all");
		}

		const response = await fetch(`${this.endpoint}?${params}`, {
			method: "DELETE",
			credentials: "same-origin",
		});

		const payload = await response.json();
		if (!response.ok) {
			this.renderer.setStatus(
				payload.error || `Request failed with status ${response.status}`,
				"error",
			);

			return;
		}

		await this.loadList(this.currentSearch);
	}

	/**
	 * @param {EntityRecord} record
	 * @returns {number}
	 */
	recordId(record) {
		if (this.entity === "tasks") {
			return /** @type {import("../types/EntityRecords").TaskRecord} */ (record).taskId;
		}

		if (this.entity === "users") {
			return /** @type {import("../types/EntityRecords").UserRecord} */ (record).userId;
		}

		return /** @type {import("../types/EntityRecords").ScheduleRecord} */ (record).user.userId;
	}

	/**
	 * @param {string} name
	 * @param {string | string[] | boolean} value
	 */
	setFieldValue(name, value) {
		const fields = this.managementForm?.querySelectorAll(`[name="${name}"]`);
		if (!fields) {
			return;
		}

		if (Array.from(fields).some((field) => field instanceof HTMLInputElement
			&& field.type === "checkbox")) {
			const selected = Array.isArray(value)
				? new Set(value)
				: value === true
					? new Set(["on"])
					: new Set();

			for (const field of fields) {
				if (field instanceof HTMLInputElement && field.type === "checkbox") {
					field.checked = selected.has(field.value) || (field.value === "on" && value === true);
				}
			}

			return;
		}

		const scalarValue = Array.isArray(value) || typeof value === "boolean"
			? value.toString()
			: value;

		for (const field of fields) {
			if (field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement) {
				field.value = scalarValue;
			} else if (field instanceof HTMLSelectElement) {
				if (field.multiple) {
					const selected = new Set(Array.isArray(value) ? value : [scalarValue]);
					for (const option of field.options) {
						option.selected = selected.has(option.value);
					}
				} else {
					field.value = scalarValue;
				}
			}
		}
	}

	resetManagementForm() {
		if (!this.managementForm) {
			return;
		}

		this.editingId = null;
		this.managementForm.reset();

		if (this.entity === "schedules") {
			this.managementForm.hidden = true;
		}

		const enabled = this.managementForm.elements.namedItem("notificationsEnabled");
		if (enabled instanceof HTMLInputElement) {
			enabled.dispatchEvent(new Event("change"));
		}

		const submit = this.managementForm.querySelector('button[type="submit"]');
		const cancel = this.managementForm.querySelector('button[type="button"]');

		if (submit instanceof HTMLButtonElement) {
			submit.textContent = `Create ${this.label.replace(/s$/, "")}`;
		}

		if (cancel instanceof HTMLButtonElement) {
			cancel.hidden = true;
		}

		const password = this.managementForm.elements.namedItem("password");
		if (password instanceof HTMLInputElement) {
			password.required = true;
			password.placeholder = "";
		}
	}

	/** @returns {Promise<void>} */
	async loadDetails() {
		const params = new URLSearchParams(window.location.search);
		if (!params.has("id")) {
			if (this.entity === "schedules") {
				throw new Error("A user ID is required to view a schedule.");
			}

			if (this.scope === "all") {
				params.set("scope", "all");
			}

			throw new Error(`A ${this.label.toLowerCase()} ID is required.`);
		}

		this.renderer.setStatus(
			`Loading ${this.label.toLowerCase()} details...`,
		);

		const record = /** @type {EntityRecord} */ (
			await this.getJson(`${this.endpoint}?${params}`)
		);

		this.renderer.renderDetails(record);
	}

	/** @returns {Promise<void>} */
	async initializeSearch() {
		const form = document.querySelector("#search-form");
		const input = document.querySelector("#search");

		if (
			!(form instanceof HTMLFormElement) ||
			!(input instanceof HTMLInputElement)
		) {
			throw new TypeError("Search page is missing its form or input.");
		}

		const initialSearch = new URLSearchParams(window.location.search).get(
			"search",
		);

		if (initialSearch !== null) {
			input.value = initialSearch;
			try {
				await this.loadList(initialSearch);
			} catch (error) {
				this.renderer.setStatus(this.errorMessage(error), "error");
			}
		}

		form.addEventListener("submit", async (event) => {
			event.preventDefault();

			const search = input.value.trim();
			const params = new URLSearchParams();
			params.set("search", search);

			history.replaceState(null, "", `?${params}`);
			try {
				await this.loadList(search);
			} catch (error) {
				this.renderer.setStatus(this.errorMessage(error), "error");
			}
		});
	}

	/**
	 * @param {unknown} error
	 * @returns {string}
	 */
	errorMessage(error) {
		if (error instanceof Error) {
			return error.message;
		}

		if (typeof error === "string") {
			return error;
		}

		try {
			return JSON.stringify(error) ?? "An unknown error occurred.";
		} catch {
			return "An unknown error occurred.";
		}
	}
}

/**
 * @param {string | undefined} value
 * @returns {value is EntityName}
 */
function isEntityName(value) {
	return value === "tasks" || value === "users" || value === "schedules";
}

/**
 * @param {Record<string, unknown> | null} user
 * @returns {boolean}
 */
function isAdmin(user) {
	return typeof user?.role === "string"
		&& user.role.split(",").some((role) => role.trim().toUpperCase() === "ADMIN");
}
