const page = document.querySelector("[data-entity][data-mode]");
const {entity, mode, label} = page.dataset;
const endpoint = `../api/business/${entity}`;
const status = document.querySelector("#status");
const results = document.querySelector("#results");

/**
 * Sets the status.
 *
 * @param {string} message
 * @param {string} kind
 */
function setStatus(message, kind = "info") {
    status.textContent = message;
    status.dataset.kind = kind;
}

/**
 * Gets JSON from the specified URL.
 *
 * @param {string} url
 * @returns {Promise<Object>}
 */
async function getJson(url) {
    const response = await fetch(url);
    const data = await response.json();

    if (!response.ok) {
        throw new Error(data.error || `Request failed with status ${response.status}`);
    }

    return data;
}

/**
 * Displays the value.
 *
 * @param {*} value
 * @returns {string}
 */
function displayValue(value) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }

    return String(value);
}

/**
 * Formats the field label.
 *
 * @param {string} name
 * @returns {string}
 */
function fieldLabel(name) {
    let label = "";

    for (let i = 0; i < name.length; i++) {
        const char = name[i];

        if (i === 0) {
            label += char.toUpperCase();
        } else if (char === char.toUpperCase()) {
            label += " ";
        } else {
            label += char;
        }
    }

    return label.trim();
}

/**
 * Adds details to the container.
 *
 * @param {HTMLElement} container
 * @param {Object} record
 */
function addDetails(container, record) {
    const details = document.createElement("dl");
    for (const [key, value] of Object.entries(record)) {
        const term = document.createElement("dt");
        const description = document.createElement("dd");

        term.textContent = fieldLabel(key);
        description.textContent = displayValue(value);

        details.append(term, description);
    }
    container.append(details);
}

/**
 * Gets the detail URL.
 *
 * @param {Object} record
 * @returns {string}
 */
function detailUrl(record) {
    const params = new URLSearchParams();

    if (entity === "schedules") {
        const {taskId, userId} = record;
        params.set("taskId", taskId);
        params.set("userId", userId);
    } else if (entity === "tasks") {
        const {taskId} = record;
        params.set("id", taskId);
    } else {
        const {userId} = record;
        params.set("id", userId);
    }

    return `detail.html?${params}`;
}

/**
 * Renders a list of records.
 *
 * @param {Object} records
 */
function renderList(records) {
    results.replaceChildren();
    if (records.length === 0) {
        setStatus(`No ${label.toLowerCase()} records found.`);
        return;
    }

    const renderHeading = (record) => {
        if (entity === "tasks") {
            const {taskTitle} = record;
            return taskTitle;
        } else if (entity === "users") {
            const {firstName, lastName, userName} = record;
            return `${firstName} ${lastName} (@${userName})`;
        } else {
            const {taskId, userId} = record;
            return `Task ${taskId} · User ${userId}`;
        }
    }

    const renderSummaryText = (record) => {
        if (entity === "users") {
            const {email, role} = record;
            return `${email} · ${role}`;
        } else if (entity === "tasks") {
            const {taskDateDue} = record;
            return `Due: ${displayValue(taskDateDue)}`;
        } else {
            const {notificationSettings} = record;
            return `Notification: ${notificationSettings}`;
        }
    }

    for (const record of records) {
        const article = document.createElement("article");
        const heading = document.createElement("h2");
        heading.textContent = renderHeading(record);
        article.append(heading);

        const summary = document.createElement("p");
        summary.textContent = renderSummaryText(record);
        article.append(summary);

        const link = document.createElement("a");
        link.href = detailUrl(record);
        link.textContent = `View ${label.toLowerCase()} details`;
        article.append(link);
        results.append(article);
    }

    setStatus(`${records.length} record${records.length === 1 ? "" : "s"} found.`);
}

/**
 * Loads the list of entities matching the search query.
 *
 * @param {string | null} search
 * @returns {Promise<void>}
 */
async function loadList(search) {
    setStatus(`Loading ${label.toLowerCase()}...`);

    const params = new URLSearchParams();
    if (search !== null) {
        params.set("search", search);
    }

    const suffix = params.size > 0 ? `?${params}` : "";
    renderList(await getJson(`${endpoint}${suffix}`));
}

async function loadDetails() {
    const params = new URLSearchParams(window.location.search);
    if (entity === "schedules") {
        if (!params.has("taskId") || !params.has("userId")) {
            throw new Error("Both task and user IDs are required to view a schedule.");
        }
    } else if (!params.has("id")) {
        throw new Error(`A ${label.toLowerCase()} ID is required.`);
    }

    setStatus(`Loading ${label.toLowerCase()} details...`);
    const record = await getJson(`${endpoint}?${params}`);

    results.replaceChildren();
    const article = document.createElement("article");
    addDetails(article, record);
    results.append(article);

    setStatus(`${label} details loaded.`);
}

/**
 * Main entry point for pages that render entities.
 *
 * @returns {Promise<void>}
 */
async function main() {
    if (mode === "list") {
        try {
            await loadList(null);
        } catch (error) {
            setStatus(error.message, "error");
        }
    } else if (mode === "detail") {
        try {
            await loadDetails();
        } catch (error) {
            setStatus(error.message, "error");
        }
    } else if (mode === "search") {
        const form = document.querySelector("#search-form");
        const input = document.querySelector("#search");
        const initialSearch = new URLSearchParams(window.location.search).get("search");

        if (initialSearch !== null) {
            input.value = initialSearch;

            try {
                await loadList(initialSearch);
            } catch (error) {
                setStatus(error.message, "error");
            }
        }

        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const search = input.value.trim();
            const params = new URLSearchParams();
            params.set("search", search);
            history.replaceState(null, "", `?${params}`);

            try {
                await loadList(search);
            } catch (error) {
                setStatus(error.message, "error");
            }
        });
    }
}

await main();
