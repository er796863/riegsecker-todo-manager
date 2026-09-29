const message = document.querySelector("#message");
const messageInput = document.querySelector("#messageInput");
const messageForm = document.querySelector("#messageForm");

try {
  const response = await fetch("api/hello");

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  const data = await response.json();
  message.textContent = data.message;
} catch (error) {
  message.textContent = "Could not load the message";
  console.error(error.message);
}

messageForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  try {
    const response = await fetch("api/hello", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: messageInput.value,
      }),
    });

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`);
    }

    const data = await response.json();
    message.textContent = data.message;
    messageForm.reset();
  } catch (error) {
    message.textContent = "Could not send the message.";
    console.error(error.message);
  }
});
