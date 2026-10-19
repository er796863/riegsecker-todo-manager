import { EntityPage } from "./classes/EntityPage.js";

const page = document.querySelector("[data-entity][data-mode]");

if (!(page instanceof HTMLElement)) {
	throw new TypeError("Unable to find entity page configuration.");
}

await new EntityPage(page).initialize();
