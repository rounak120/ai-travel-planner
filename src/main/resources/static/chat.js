"use strict";

const form = document.querySelector("#chat-form");
const message = document.querySelector("#message");
const tripId = document.querySelector("#trip-id");
const conversation = document.querySelector("#conversation");
const status = document.querySelector("#status");
const count = document.querySelector("#count");
const send = document.querySelector("#send");
let pending = false;
const linkedTrip = new URLSearchParams(window.location.search).get("tripId");
if (linkedTrip && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(linkedTrip)) {
  tripId.value = linkedTrip; // Prefill only; never submit or call a provider on navigation.
}

function setStatus(text, error = false) {
  status.textContent = text;
  status.classList.toggle("error", error);
}

function element(tag, text, className) {
  const node = document.createElement(tag);
  node.textContent = text; // User, model and provider text must never become executable HTML.
  if (className) node.className = className;
  return node;
}

function renderAnswer(question, selectedTrip, answer) {
  document.querySelector("#empty")?.remove();
  const turn = element("article", "", "turn");
  turn.append(element("h3", "You"), element("p", question, "question"));
  if (selectedTrip) turn.append(element("p", `Trip: ${selectedTrip}`, "hint"));
  turn.append(element("h3", "Assistant"), element("p", answer.message));
  if (answer.tools.length === 0) turn.append(element("p", "No tools used. This answer is not a live lookup.", "hint"));
  for (const tool of answer.tools) {
    const failed = tool.result?.isError === true;
    const details = element("details", "", `evidence${failed ? " failed" : ""}`);
    details.append(element("summary", `${tool.name} · ${failed ? "Tool reported an error" : "Tool result"}`));
    details.append(element("pre", JSON.stringify(tool.result, null, 2)));
    turn.append(details);
  }
  conversation.append(turn);
  // Keep the visible transcript bounded; it is never sent back as model memory.
  while (conversation.children.length > 20) conversation.firstElementChild.remove();
  conversation.scrollTop = conversation.scrollHeight;
}

message.addEventListener("input", () => { count.textContent = `${message.value.length} / 2000`; });
document.querySelectorAll(".suggestion").forEach(button => button.addEventListener("click", () => {
  message.value = button.dataset.question;
  message.dispatchEvent(new Event("input"));
  message.focus();
}));
document.querySelector("#clear").addEventListener("click", () => {
  conversation.replaceChildren(element("p", "View cleared. Your next question starts fresh.", "hint"));
  setStatus("Only this page’s display was cleared. No saved trips were changed.");
});

form.addEventListener("submit", async event => {
  event.preventDefault();
  if (pending) return;
  const question = message.value.trim();
  const selectedTrip = tripId.value.trim();
  if (!question || message.value.length > 2000) {
    setStatus("Enter a question of 1–2000 characters.", true);
    message.focus();
    return;
  }
  if (selectedTrip && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(selectedTrip)) {
    setStatus("Enter a complete trip UUID, or leave Trip ID empty.", true);
    tripId.focus();
    return;
  }
  pending = true;
  document.querySelectorAll("button, input, textarea").forEach(node => { node.disabled = true; });
  send.textContent = "Working…";
  form.setAttribute("aria-busy", "true");
  setStatus("Waiting for local Ollama and any requested tools. This can take a few minutes; no automatic retries.");
  try {
    const body = { message: question };
    if (selectedTrip) body.tripId = selectedTrip;
    const response = await fetch("/api/v1/ai/chat", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
    });
    if (response.status === 404) throw new Error("Chat is not enabled. Start the app with profiles ai,mcp,mcp-client.");
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(typeof data?.detail === "string" ? data.detail : `Request failed (HTTP ${response.status}).`);
    if (typeof data?.message !== "string" || !Array.isArray(data.tools)) throw new Error("The server returned an unexpected response.");
    renderAnswer(question, selectedTrip, data);
    message.value = "";
    count.textContent = "0 / 2000";
    setStatus(data.tools.some(tool => tool.result?.isError === true)
      ? "Answer received, but a tool reported an error. Expand its result for details."
      : "Answer received. Check tool evidence before relying on live information.");
  } catch (error) {
    setStatus(error instanceof TypeError ? "Could not reach the app. Check that it is running. Your question has been kept." : error.message, true);
  } finally {
    pending = false;
    document.querySelectorAll("button, input, textarea").forEach(node => { node.disabled = false; });
    send.textContent = "Ask assistant ↗";
    form.removeAttribute("aria-busy");
    message.focus();
  }
});
