/**
 * @typedef {{
 *   notificationId: number,
 *   scheduledDate: string,
 *   scheduledTime: string,
 *   notificationTimezone: string,
 *   read: boolean,
 *   additionalTaskCount: number,
 *   tasks: Array<{taskId: number, taskTitle: string, taskDateDue: string | null}>
 * }} InAppNotification
 */

/**
 * @param {string} contextPath
 * @param {Element} accountNavigation
 * @returns {void}
 */
export function initializeNotificationsWidget(contextPath, accountNavigation) {
	const root = document.createElement("div");
	root.className = "notification-widget";

	const toggle = document.createElement("button");
	toggle.type = "button";
	toggle.className = "notification-toggle";
	toggle.setAttribute("aria-expanded", "false");
	toggle.setAttribute("aria-controls", "notification-panel");
	toggle.setAttribute("aria-label", "Notifications");

	const icon = document.createElement("span");
	icon.setAttribute("aria-hidden", "true");
	icon.textContent = "🔔";

	const count = document.createElement("span");
	count.className = "notification-count";
	count.hidden = true;
	toggle.append(icon, count);

	const panel = document.createElement("section");
	panel.id = "notification-panel";
	panel.className = "notification-panel";
	panel.hidden = true;
	panel.setAttribute("aria-label", "Notifications");

	const panelHeading = document.createElement("h2");
	panelHeading.textContent = "Notifications";

	const clearAll = document.createElement("button");
	clearAll.type = "button";
	clearAll.className = "notification-clear-all";
	clearAll.textContent = "Clear all";

	const header = document.createElement("div");
	header.className = "notification-panel-header";
	header.append(panelHeading, clearAll);

	const status = document.createElement("output");
	status.className = "notification-status";
	status.setAttribute("aria-live", "polite");

	const list = document.createElement("ul");
	list.className = "notification-list";
	panel.append(header, status, list);
	root.append(toggle, panel);

	accountNavigation.append(root);

	const endpoint = `${contextPath}/api/notifications`;
	/** @type {InAppNotification[]} */ let notifications = [];

	/** @param {unknown} error */
	const setError = (error) => {
		status.textContent = error instanceof Error
			? error.message
			: "Unable to load notifications.";
		status.dataset.kind = "error";
	};

	/**
	 * @param {string} url
	 * @param {RequestInit} [options]
	 * @returns {Promise<unknown>}
	 */
	const request = async (url, options = {}) => {
		const response = await fetch(url, {
			credentials: "same-origin",
			...options,
		});

		const payload = await response.json();

		if (!response.ok) {
			const message = typeof payload?.error === "string"
				? payload.error
				: `Request failed with status ${response.status}`;
			throw new Error(message);
		}

		return payload;
	};

	/** @param {boolean} processSchedule */
	const refresh = async (processSchedule) => {
		status.textContent = "";
		status.dataset.kind = "info";
		try {
			if (processSchedule) {
				await request(`${endpoint}/check`, { method: "POST" });
			}

			const payload = await request(endpoint);

			if (!Array.isArray(payload) || !payload.every(isNotification)) {
				throw new TypeError("The notification response is invalid.");
			}

			notifications = payload;
			render();
		} catch (error) {
			setError(error);
		}
	};

	const render = () => {
		const unreadCount = notifications.filter(
			/** @param {InAppNotification} notification */
			(notification) => !notification.read,
		).length;

		count.hidden = unreadCount === 0;
		count.textContent = unreadCount > 99 ? "99+" : String(unreadCount);

		toggle.setAttribute("aria-label", `Notifications, ${unreadCount} unread`);

		clearAll.disabled = notifications.length === 0;
		list.replaceChildren();

		if (notifications.length === 0) {
			const empty = document.createElement("li");
			empty.className = "notification-empty";
			empty.textContent = "You have no notifications.";
			list.append(empty);

			return;
		}

		for (const notification of notifications) {
			list.append(renderNotification(notification));
		}
	};

	/** @param {InAppNotification} notification */
	const renderNotification = (notification) => {
		const item = document.createElement("li");
		item.className = notification.read
			? "notification-item is-read"
			: "notification-item is-unread";

		const heading = document.createElement("h3");
		heading.textContent = "Your scheduled tasks";

		const scheduled = document.createElement("p");
		scheduled.className = "notification-scheduled";
		scheduled.textContent =
			`Scheduled for ${notification.scheduledDate} at ${notification.scheduledTime} `
			+ `(${notification.notificationTimezone})`;

		const taskList = document.createElement("ul");
		taskList.className = "notification-tasks";

		for (const task of notification.tasks) {
			const taskItem = document.createElement("li");
			taskItem.textContent = task.taskTitle;

			if (task.taskDateDue) {
				taskItem.append(document.createTextNode(` · Due ${task.taskDateDue.replace("T", " ")}`));
			}

			taskList.append(taskItem);
		}

		item.append(heading, scheduled, taskList);

		if (notification.additionalTaskCount > 0) {
			const more = document.createElement("p");
			more.className = "notification-more";
			more.textContent = `${notification.additionalTaskCount} more scheduled task${
				notification.additionalTaskCount === 1 ? "" : "s"
			} not shown.`;
			item.append(more);
		}

		const actions = document.createElement("div");
		actions.className = "notification-actions";

		if (!notification.read) {
			const markRead = document.createElement("button");
			markRead.type = "button";
			markRead.textContent = "Mark as read";

			markRead.addEventListener("click", async () => {
				markRead.disabled = true;

				try {
					await request(`${endpoint}/read?id=${notification.notificationId}`, {
						method: "PUT",
					});

					await refresh(false);
				} catch (error) {
					markRead.disabled = false;
					setError(error);
				}
			});

			actions.append(markRead);
		} else {
			const readLabel = document.createElement("span");
			readLabel.className = "notification-read-label";
			readLabel.textContent = "Read";
			actions.append(readLabel);
		}

		const clear = document.createElement("button");
		clear.type = "button";
		clear.className = "notification-clear";
		clear.textContent = "Clear";

		clear.addEventListener("click", async () => {
			clear.disabled = true;

			try {
				await request(`${endpoint}?id=${notification.notificationId}`, {
					method: "DELETE",
				});

				await refresh(false);
			} catch (error) {
				clear.disabled = false;
				setError(error);
			}
		});

		actions.append(clear);
		item.append(actions);

		return item;
	};

	toggle.addEventListener("click", async () => {
		const isOpen = !panel.hidden;
		panel.hidden = isOpen;
		toggle.setAttribute("aria-expanded", String(!isOpen));

		if (!isOpen) {
			void refresh(true);
		}
	});

	clearAll.addEventListener("click", async () => {
		if (!window.confirm("Permanently clear all notifications?")) {
			return;
		}

		clearAll.disabled = true;

		try {
			await request(endpoint, { method: "DELETE" });
			await refresh(false);
		} catch (error) {
			clearAll.disabled = false;
			setError(error);
		}
	});

	document.addEventListener("click", (event) => {
		if (!root.contains(event.target instanceof Node ? event.target : null)) {
			panel.hidden = true;
			toggle.setAttribute("aria-expanded", "false");
		}
	});

	document.addEventListener("keydown", (event) => {
		if (event.key === "Escape") {
			panel.hidden = true;
			toggle.setAttribute("aria-expanded", "false");
		}
	});

	void refresh(true);
}

/**
 * @param {unknown} value
 * @returns {value is InAppNotification}
 */
function isNotification(value) {
	if (typeof value !== "object" || value === null) {
		return false;
	}

	const notification = /** @type {Record<string, unknown>} */ (value);

	return typeof notification.notificationId === "number"
		&& typeof notification.scheduledDate === "string"
		&& typeof notification.scheduledTime === "string"
		&& typeof notification.notificationTimezone === "string"
		&& typeof notification.read === "boolean"
		&& typeof notification.additionalTaskCount === "number"
		&& Array.isArray(notification.tasks)
		&& notification.tasks.every((task) => typeof task === "object"
			&& task !== null
			&& typeof task.taskId === "number"
			&& typeof task.taskTitle === "string"
			&& (typeof task.taskDateDue === "string" || task.taskDateDue === null));
}
