/**
 * @typedef {import("../types/EntityRecords").EntityName} EntityName
 * @typedef {import("../types/EntityRecords").EntityRecord} EntityRecord
 * @typedef {import("../types/EntityRecords").EntityRecordCollection} EntityRecordCollection
 * @typedef {import("../types/EntityRecords").ScheduleRecord} ScheduleRecord
 * @typedef {import("../types/EntityRecords").TaskRecord} TaskRecord
 * @typedef {import("../types/EntityRecords").UserRecord} UserRecord
 */

export class EntityRenderer {
	/**
	 * @param {{
	 *   entity: EntityName,
	 *   label: string,
	 *   statusOutput: HTMLOutputElement,
	 *   results: HTMLElement
	 * }} options
	 */
	constructor({ entity, label, statusOutput, results }) {
		this.entity = entity;
		this.label = label;
		this.statusOutput = statusOutput;
		this.results = results;
	}

	/**
	 * @param {string} message
	 * @param {string} [kind]
	 */
	setStatus(message, kind = "info") {
		this.statusOutput.textContent = message;
		this.statusOutput.dataset.kind = kind;
	}

	/**
	 * @param {EntityRecordCollection} records
	 */
	renderList(records) {
		if (!Array.isArray(records)) {
			records = Object.values(records);
		}

		this.results.replaceChildren();
		if (records.length === 0) {
			this.setStatus(`No ${this.label.toLowerCase()} records found.`);
			return;
		}

		if (this.entity === "schedules") {
			for (const schedule of records) {
				this.results.append(
					this.createScheduleGroup(
						/** @type {ScheduleRecord} */ (schedule),
					),
				);
			}
		} else {
			for (const record of records) {
				this.results.append(this.createRecordCard(record));
			}
		}

		this.setStatus(
			`${records.length} record${records.length === 1 ? "" : "s"} found.`,
		);
	}

	/**
	 * @param {EntityRecord} record
	 */
	renderDetails(record) {
		this.results.replaceChildren();

		if (this.entity === "schedules") {
			const schedule = /** @type {ScheduleRecord} */ (record);
			const { firstName, lastName, userName, email, role } = schedule.user;
			this.results.append(
				this.createScheduleDetailCard(
					`${firstName} ${lastName} (@${userName})`,
					`${email} · ${role}`,
					schedule.user,
				),
			);

			for (const task of Object.values(schedule.tasks)) {
				this.results.append(
					this.createScheduleDetailCard(
						task.taskTitle,
						`Due: ${this.displayValue(task.taskDateDue)}`,
						task,
					),
				);
			}

			this.results.append(
				this.createScheduleDetailCard(
					"Notification settings",
					schedule.notificationSettings,
					{ notificationSettings: schedule.notificationSettings },
				),
			);
		} else {
			const article = document.createElement("article");
			this.addDetails(article, record);
			this.results.append(article);
		}

		this.setStatus(`${this.label} details loaded.`);
	}

	/**
	 * @param {string} title
	 * @param {string} summary
	 * @param {TaskRecord | UserRecord | { notificationSettings: string }} record
	 * @returns {HTMLElement}
	 */
	createScheduleDetailCard(title, summary, record) {
		const article = document.createElement("article");
		article.className = "schedule-detail-card";

		const heading = document.createElement("h2");
		heading.textContent = title;
		article.append(heading);

		const summaryElement = document.createElement("p");
		summaryElement.textContent = summary;
		article.append(summaryElement);

		this.addDetails(article, record);
		return article;
	}

	/**
	 * @param {ScheduleRecord} schedule
	 * @returns {HTMLElement}
	 */
	createScheduleGroup(schedule) {
		const { firstName, lastName, userName } = schedule.user;
		const group = document.createElement("details");
		group.className = "schedule-group";

		const heading = document.createElement("summary");
		heading.textContent = `${firstName} ${lastName} (@${userName})`;
		group.append(heading);

		const cards = document.createElement("div");
		cards.className = "schedule-group-cards";
		for (const task of Object.values(schedule.tasks)) {
			cards.append(this.createRecordCard(schedule, task));
		}
		group.append(cards);
		return group;
	}

	/**
	 * @param {EntityRecord} record
	 * @param {TaskRecord} [scheduleTask]
	 * @returns {HTMLElement}
	 */
	createRecordCard(record, scheduleTask) {
		const article = document.createElement("article");
		const heading = document.createElement("h2");
		heading.textContent = this.recordHeading(record, scheduleTask);
		article.append(heading);

		const summary = document.createElement("p");
		summary.textContent = this.recordSummary(record);
		article.append(summary);

		const link = document.createElement("a");
		link.href = this.detailUrl(record);
		link.textContent = `View ${this.label.toLowerCase()} details`;
		article.append(link);
		return article;
	}

	/**
	 * @param {EntityRecord} record
	 * @param {TaskRecord} [scheduleTask]
	 * @returns {string}
	 */
	recordHeading(record, scheduleTask) {
		if (this.entity === "tasks") {
			return /** @type {TaskRecord} */ (record).taskTitle;
		}
		if (this.entity === "users") {
			const { firstName, lastName, userName } =
				/** @type {UserRecord} */ (record);
			return `${firstName} ${lastName} (@${userName})`;
		}
		if (!scheduleTask) {
			throw new Error("A task is required to render a schedule entry.");
		}
		return scheduleTask.taskTitle;
	}

	/**
	 * @param {EntityRecord} record
	 * @returns {string}
	 */
	recordSummary(record) {
		if (this.entity === "users") {
			const { email, role } = /** @type {UserRecord} */ (record);
			return `${email} · ${role}`;
		}
		if (this.entity === "tasks") {
			return `Due: ${this.displayValue(/** @type {TaskRecord} */ (record).taskDateDue)}`;
		}
		return `Notification: ${/** @type {ScheduleRecord} */ (record).notificationSettings}`;
	}

	/**
	 * @param {EntityRecord} record
	 * @returns {string}
	 */
	detailUrl(record) {
		const params = new URLSearchParams();
		if (this.entity === "schedules") {
			params.set(
				"id",
				String(/** @type {ScheduleRecord} */ (record).user.userId),
			);
		} else if (this.entity === "tasks") {
			params.set("id", String(/** @type {TaskRecord} */ (record).taskId));
		} else {
			params.set("id", String(/** @type {UserRecord} */ (record).userId));
		}
		return `detail.html?${params}`;
	}

	/**
	 * @param {HTMLElement} container
	 * @param {TaskRecord | UserRecord | { notificationSettings: string }} record
	 */
	addDetails(container, record) {
		const details = document.createElement("dl");
		for (const [key, value] of Object.entries(record)) {
			const term = document.createElement("dt");
			const description = document.createElement("dd");
			term.textContent = this.fieldLabel(key);
			description.textContent = this.displayValue(value);
			details.append(term, description);
		}
		container.append(details);
	}

	/**
	 * @param {unknown} value
	 * @returns {string}
	 */
	displayValue(value) {
		if (value === null || value === undefined || value === "") {
			return "-";
		}

		return JSON.stringify(value) ?? "-";
	}

	/**
	 * @param {string} name
	 * @returns {string}
	 */
	fieldLabel(name) {
		let label = "";
		for (let i = 0; i < name.length; i++) {
			const char = name[i];
			if (i === 0) {
				label += char.toUpperCase();
			} else if (char === char.toUpperCase()) {
				label += ` ${char}`;
			} else {
				label += char;
			}
		}
		return label.trim();
	}
}
