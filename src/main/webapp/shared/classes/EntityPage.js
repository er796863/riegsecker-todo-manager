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
		this.endpoint = `../api/business/${entity}`;

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
			statusOutput,
			results,
		});
	}

	/** @returns {Promise<void>} */
	async initialize() {
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
		this.renderer.setStatus(`Loading ${this.label.toLowerCase()}...`);

		const params = new URLSearchParams();
		if (search !== null) {
			params.set("search", search);
		}
		const suffix = params.size > 0 ? `?${params}` : "";

		const records = /** @type {EntityRecordCollection} */ (
			await this.getJson(`${this.endpoint}${suffix}`)
		);
		this.renderer.renderList(records);
	}

	/** @returns {Promise<void>} */
	async loadDetails() {
		const params = new URLSearchParams(window.location.search);
		if (!params.has("id")) {
			if (this.entity === "schedules") {
				throw new Error("A user ID is required to view a schedule.");
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
