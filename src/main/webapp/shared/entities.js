const page = document.querySelector("[data-entity][data-mode]");
const { entity, mode, label } = page.dataset;
const endpoint = `../api/business/${entity}`;
const status = document.querySelector("#status");
const results = document.querySelector("#results");

function setStatus(message, kind = "info") {
  status.textContent = message;
  status.dataset.kind = kind;
}

async function getJson(url) {
  const response = await fetch(url);
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || `Request failed with status ${response.status}`);
  }
  return data;
}

function displayValue(value) {
  return value === null || value === undefined || value === "" ? "—" : String(value);
}

function fieldLabel(name) {
  return name.replace(/([A-Z])/g, " $1").replace(/^./, (first) => first.toUpperCase());
}

function addDetails(container, record) {
  const details = document.createElement("dl");
  for (const [key, value] of Object.entries(record)) {
    const term = document.createElement("dt");
    term.textContent = fieldLabel(key);
    const description = document.createElement("dd");
    description.textContent = displayValue(value);
    details.append(term, description);
  }
  container.append(details);
}

function detailUrl(record) {
  const params = new URLSearchParams();
  if (entity === "schedules") {
    params.set("taskId", record.taskId);
    params.set("userId", record.userId);
  } else {
    params.set("id", entity === "tasks" ? record.taskId : record.userId);
  }
  return `detail.html?${params}`;
}

function renderList(records) {
  results.replaceChildren();
  if (records.length === 0) {
    setStatus(`No ${label.toLowerCase()} records found.`);
    return;
  }

  for (const record of records) {
    const article = document.createElement("article");
    const heading = document.createElement("h2");
    if (entity === "tasks") {
      heading.textContent = record.taskTitle;
    } else if (entity === "users") {
      heading.textContent = `${record.firstName} ${record.lastName} (@${record.userName})`;
    } else {
      heading.textContent = `Task ${record.taskId} · User ${record.userId}`;
    }
    article.append(heading);

    const summary = document.createElement("p");
    summary.textContent = entity === "tasks"
      ? `Due: ${displayValue(record.taskDateDue)}`
      : entity === "users"
        ? `${record.email} · ${record.role}`
        : `Notification: ${record.notificationSettings}`;
    article.append(summary);

    const link = document.createElement("a");
    link.href = detailUrl(record);
    link.textContent = `View ${label.toLowerCase()} details`;
    article.append(link);
    results.append(article);
  }
  setStatus(`${records.length} record${records.length === 1 ? "" : "s"} found.`);
}

async function loadList(search) {
  setStatus(`Loading ${label.toLowerCase()}...`);
  const params = new URLSearchParams();
  if (search !== null) {
    params.set("search", search);
  }
  const suffix = params.size ? `?${params}` : "";
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

if (mode === "list") {
  loadList(null).catch((error) => setStatus(error.message, "error"));
} else if (mode === "detail") {
  loadDetails().catch((error) => setStatus(error.message, "error"));
} else if (mode === "search") {
  const form = document.querySelector("#search-form");
  const input = document.querySelector("#search");
  const initialSearch = new URLSearchParams(window.location.search).get("search");
  if (initialSearch !== null) {
    input.value = initialSearch;
    loadList(initialSearch).catch((error) => setStatus(error.message, "error"));
  }
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const search = input.value.trim();
    const params = new URLSearchParams();
    params.set("search", search);
    history.replaceState(null, "", `?${params}`);
    loadList(search).catch((error) => setStatus(error.message, "error"));
  });
}
