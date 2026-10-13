"use strict";
const tripForm = document.querySelector("#trip-form");
const tripStatus = document.querySelector("#trip-status");
const tripErrors = document.querySelector("#trip-errors");
const savedTrip = document.querySelector("#saved-trip");
const field = id => document.getElementById(id);
let saving = false;
let saved = false;

function reportTrip(text, error = false) {
  tripStatus.textContent = text;
  tripStatus.classList.toggle("error", error);
}
function lockTrip(locked) {
  tripForm.querySelectorAll("input, button").forEach(node => { node.disabled = locked; });
}
tripForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (saving || saved) return;
  tripErrors.replaceChildren();
  if (!tripForm.reportValidity()) return;
  const interests = field("interests").value.split(",").map(value => value.trim()).filter(Boolean);
  const destination = field("destination").value.trim();
  if (!destination) { reportTrip("Enter a destination.", true); return; }
  if (field("end-date").value < field("start-date").value) { reportTrip("End date must be on or after start date.", true); return; }
  if (interests.length > 20 || interests.some(value => value.length > 80)) { reportTrip("Use at most 20 interests, each at most 80 characters.", true); return; }
  const body = { destination, startDate: field("start-date").value, endDate: field("end-date").value,
    travelers: Number(field("travelers").value), budget: field("budget").value,
    currency: field("currency").value.trim().toUpperCase(), interests };
  saving = true;
  lockTrip(true);
  tripForm.setAttribute("aria-busy", "true");
  reportTrip("Saving your trip…");
  try {
    const response = await fetch("/api/v1/trips", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    const data = await response.json().catch(() => null);
    if (!response.ok) {
      if (data?.errors && typeof data.errors === "object") {
        for (const [name, detail] of Object.entries(data.errors)) {
          const item = document.createElement("li"); item.textContent = `${name}: ${String(detail)}`; tripErrors.append(item);
        }
      }
      throw new Error(typeof data?.detail === "string" ? data.detail : `Could not save trip (HTTP ${response.status}).`);
    }
    if (response.status !== 201 || typeof data?.id !== "string" || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.id)) {
      throw new Error("The server returned an unexpected result. The trip may have been saved; check before submitting again.");
    }
    saved = true;
    field("saved-id").value = data.id;
    field("saved-summary").textContent = `${data.destination} · ${data.startDate} to ${data.endDate} · ${data.travelers} travelers`;
    field("open-chat").href = `/?tripId=${encodeURIComponent(data.id)}`;
    field("select-location").href = `/location.html?tripId=${encodeURIComponent(data.id)}`;
    field("open-trip").href = `/api/v1/trips/${encodeURIComponent(data.id)}`;
    savedTrip.hidden = false;
    reportTrip("Trip saved. A verified location is still needed for weather.");
    field("saved-id").focus();
  } catch (error) {
    reportTrip(error instanceof TypeError ? "Connection lost. The trip may have been saved. Check before submitting again; no automatic retry was made." : error.message, true);
  } finally {
    saving = false;
    lockTrip(saved);
    tripForm.removeAttribute("aria-busy");
  }
});
field("another-trip").addEventListener("click", () => {
  if (saving) return;
  saved = false; savedTrip.hidden = true; lockTrip(false); tripForm.reset();
  tripErrors.replaceChildren(); reportTrip("Ready for another trip. The previous trip remains saved."); field("destination").focus();
});
