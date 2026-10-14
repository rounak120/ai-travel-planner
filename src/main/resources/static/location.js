"use strict";
const loc = id => document.getElementById(id);
const uuid = value => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
let loadedTrip = null;
let searchSnapshot = null;
let chosenId = null;
let locationBusy = false;
const linkedLocationTrip = new URLSearchParams(window.location.search).get("tripId");
if (linkedLocationTrip && uuid(linkedLocationTrip)) loc("location-trip-id").value = linkedLocationTrip;

function locationStatus(text, error = false) {
  loc("location-status").textContent = text;
  loc("location-status").classList.toggle("error", error);
}
function locationControls() {
  document.querySelectorAll("input, button").forEach(node => { node.disabled = locationBusy; });
  loc("location-query").disabled = locationBusy || !loadedTrip;
  loc("search-location").disabled = locationBusy || !loadedTrip;
  loc("save-location").disabled = locationBusy || !loadedTrip || chosenId === null;
}
function clearCandidates() {
  searchSnapshot = null; chosenId = null;
  loc("location-candidates").replaceChildren(); loc("location-results").hidden = true;
  locationControls();
}
function describePlace(place) {
  return `${place.name} · ${place.country || "Country unavailable"} · ${place.region || "Region unavailable"}\nCoordinates: ${place.latitude}, ${place.longitude} · Timezone: ${place.timezone || "Unavailable — weather will not work"}`;
}
function showCurrent(selection) {
  loc("current-location").textContent = selection ? describePlace(selection.place) : "No selected location yet.";
  loc("current-attribution").textContent = selection ? `${selection.attribution || ""} ${selection.sourceUrl || ""}` : "";
}
async function locationRequest(url, options) {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(typeof data?.detail === "string" ? data.detail : `Request failed (HTTP ${response.status}).`);
    error.status = response.status; throw error;
  }
  if (!data) throw new Error("The server returned an unexpected response.");
  return data;
}
function locationFailure(error, saving = false) {
  locationStatus(error instanceof TypeError
    ? (saving ? "Connection lost. The selection may have been saved. Load the trip again to check; no automatic retry was made." : "Could not reach the app. Check that it is running.")
    : error.message, true);
}
loc("location-trip-id").addEventListener("input", () => {
  window.Wayfarer?.setTrip(null);
  loc("location-itinerary").hidden = true;
  loadedTrip = null; loc("trip-context").hidden = true; loc("location-chat").hidden = true;
  clearCandidates(); locationStatus("Load this trip before searching or saving.");
});
loc("location-query").addEventListener("input", () => { clearCandidates(); });

loc("load-trip-form").addEventListener("submit", async event => {
  event.preventDefault(); if (locationBusy) return;
  const id = loc("location-trip-id").value.trim();
  if (!uuid(id)) { locationStatus("Enter a complete trip UUID.", true); return; }
  loadedTrip = null; clearCandidates(); loc("trip-context").hidden = true; loc("location-chat").hidden = true;
  window.Wayfarer?.setTrip(null);
  loc("location-itinerary").hidden = true;
  locationBusy = true; locationControls(); locationStatus("Loading trip and saved location…");
  try {
    const trip = await locationRequest(`/api/v1/trips/${id}`);
    let selection = null;
    try { selection = await locationRequest(`/api/v1/trips/${id}/location`); }
    catch (error) { if (error.status !== 404) throw error; }
    if (typeof trip.id !== "string" || trip.id.toLowerCase() !== id.toLowerCase()
        || (selection && (!selection.place || selection.tripId?.toLowerCase() !== id.toLowerCase()))) throw new Error("Unexpected trip or location response.");
    loadedTrip = trip;
    window.Wayfarer?.setTrip(trip);
    loc("location-trip-summary").textContent = `${trip.destination} · ${trip.startDate} to ${trip.endDate}`;
    loc("location-query").value = String(trip.destination).slice(0, 100);
    showCurrent(selection); loc("trip-context").hidden = false;
    loc("location-chat").href = `/?tripId=${encodeURIComponent(id)}`; loc("location-chat").hidden = false;
    loc("location-itinerary").href = `/itinerary.html?tripId=${encodeURIComponent(id)}`; loc("location-itinerary").hidden = false;
    locationStatus("Trip loaded. Search and choose a result; nothing has been changed.");
  } catch (error) { locationFailure(error); }
  finally { locationBusy = false; locationControls(); }
});

loc("search-location-form").addEventListener("submit", async event => {
  event.preventDefault(); if (locationBusy || !loadedTrip) return;
  const query = loc("location-query").value.trim();
  clearCandidates();
  if (query.length < 2 || query.length > 100 || /[\x00-\x1f\x7f-\x9f]/.test(query)) { locationStatus("Use 2–100 characters without control characters.", true); return; }
  locationBusy = true; locationControls(); locationStatus("Searching destinations…");
  try {
    const data = await locationRequest(`/api/v1/destinations?name=${encodeURIComponent(query)}`);
    if (!Array.isArray(data.results) || data.results.some(place => !Number.isSafeInteger(place.id) || place.id <= 0 || typeof place.name !== "string")) throw new Error("Unexpected destination search response.");
    searchSnapshot = { query, tripId: loadedTrip.id };
    for (const place of data.results) {
      const label = document.createElement("label"); label.className = "location-option";
      const radio = document.createElement("input"); radio.type = "radio"; radio.name = "candidate"; radio.value = String(place.id);
      const description = document.createElement("span"); description.textContent = describePlace(place);
      radio.addEventListener("change", () => { chosenId = place.id; locationControls(); });
      label.append(radio, description); loc("location-candidates").append(label);
    }
    loc("search-attribution").textContent = `${data.attribution || ""} ${data.sourceUrl || ""}`;
    loc("location-results").hidden = false;
    locationStatus(data.results.length ? "Review the matches, choose one, then save explicitly." : "No matches. Try a different destination name.");
  } catch (error) { clearCandidates(); locationFailure(error); }
  finally { locationBusy = false; locationControls(); }
});

loc("save-location").addEventListener("click", async () => {
  if (locationBusy || !loadedTrip || !searchSnapshot || chosenId === null || searchSnapshot.tripId !== loadedTrip.id) return;
  const snapshot = searchSnapshot;
  const placeId = chosenId;
  locationBusy = true; locationControls(); locationStatus("Verifying and saving your selected location…");
  try {
    const selection = await locationRequest(`/api/v1/trips/${encodeURIComponent(snapshot.tripId)}/location`, {
      method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ searchName: snapshot.query, placeId })
    });
    if (!selection.place || selection.tripId !== snapshot.tripId || selection.place.id !== placeId) throw new Error("Unexpected save response. Load the trip again to check whether the selection was saved.");
    showCurrent(selection); locationStatus("Location saved. Weather still requires valid timezone and eligible trip dates.");
  } catch (error) { locationFailure(error, true); }
  finally { clearCandidates(); locationBusy = false; locationControls(); }
});
