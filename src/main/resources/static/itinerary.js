"use strict";
const planEl = id => document.getElementById(id);
const planUuid = value => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
let planTrip = null;
let planBusy = false;
let historyCursor = null;
const historyIds = new Set();
function resetHistory() {
  historyCursor = null; historyIds.clear();
  planEl("history-list").replaceChildren(); planEl("history-more").hidden = true;
  historyStatus("Load history to browse saved snapshots. This does not call AI.");
}
function historyStatus(text, error = false) {
  planEl("history-status").textContent = text;
  planEl("history-status").classList.toggle("error", error);
}
const linkedPlanTrip = new URLSearchParams(window.location.search).get("tripId");
if (linkedPlanTrip && planUuid(linkedPlanTrip)) planEl("plan-trip-id").value = linkedPlanTrip;
function planStatus(text, error = false) { planEl("plan-status").textContent = text; planEl("plan-status").classList.toggle("error", error); }
function planControls() {
  planEl("plan-trip-id").disabled = planBusy; planEl("plan-load").disabled = planBusy;
  planEl("plan-confirm").disabled = planBusy || !planTrip;
  planEl("plan-generate").disabled = planBusy || !planTrip || !planEl("plan-confirm").checked;
  planEl("history-refresh").disabled = planBusy || !planTrip;
  planEl("history-more").disabled = planBusy || !planTrip || historyCursor === null;
  planEl("history-list").querySelectorAll("button").forEach(button => { button.disabled = planBusy || !planTrip; });
}
function planNode(tag, text, className) { const node = document.createElement(tag); node.textContent = text; if (className) node.className = className; return node; }
async function planRequest(url, method = "GET") {
  const response = await fetch(url, { method });
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(typeof data?.detail === "string" ? data.detail : `Request failed (HTTP ${response.status}).`);
    error.status = response.status; throw error;
  }
  if (!data) throw new Error("Unexpected response. Reload the latest plan before attempting generation again.");
  return data;
}
function showPlan(plan, id, label = "Latest saved plan at load time") {
  if (plan.tripId?.toLowerCase() !== id.toLowerCase() || !Array.isArray(plan.days) || !Array.isArray(plan.warnings)) throw new Error("Unexpected itinerary response. Load the latest plan again.");
  const fragment = document.createDocumentFragment();
  fragment.append(planNode("p", label, "plan-version-label"));
  fragment.append(planNode("h2", `Saved itinerary · ${plan.destination}`));
  fragment.append(planNode("p", `Estimated activity total: ${plan.estimatedActivityCostTotal} ${plan.currency} · Trip budget: ${plan.tripBudget} ${plan.currency}`));
  const warnings = planNode("ul", "", "plan-warnings");
  for (const warning of plan.warnings) warnings.append(planNode("li", warning));
  fragment.append(warnings);
  const place = plan.selectedLocation?.place;
  fragment.append(planNode("p", place ? `Location snapshot: ${place.name}, ${place.region || "region unavailable"}, ${place.country || "country unavailable"} · ${place.timezone || "timezone unavailable"}` : "No selected-location snapshot in this saved plan.", "hint"));
  const weather = plan.weatherContext;
  fragment.append(planNode("p", weather ? `Saved weather: ${weather.status} · ${weather.message}` : "No weather snapshot in this saved plan.", "hint"));
  if (weather?.forecast?.fetchedAt) fragment.append(planNode("p", `Forecast fetched at ${weather.forecast.fetchedAt}. This is stored context, not a fresh forecast.`, "hint"));
  for (const day of plan.days) {
    if (!Array.isArray(day.activities)) throw new Error("Invalid itinerary day. Load the latest plan again.");
    const section = planNode("section", "", "plan-day"); section.append(planNode("h3", `Day ${day.dayNumber} · ${day.date}`));
    for (const activity of day.activities) section.append(planNode("h4", activity.title), planNode("p", activity.description), planNode("p", `Estimated cost: ${activity.estimatedCost} ${plan.currency}`, "hint"));
    section.append(planNode("p", `Day total: ${day.estimatedActivityCost} ${plan.currency}`)); fragment.append(section);
  }
  const evidence = planNode("details", "", "evidence"); evidence.append(planNode("summary", "Full saved snapshot and attribution"), planNode("pre", JSON.stringify(plan, null, 2))); fragment.append(evidence);
  planEl("plan-output").replaceChildren(fragment); planEl("plan-output").hidden = false;
  const exports = planNode("div", "", "export-actions");
  const download = planNode("button", "Download this snapshot", "secondary"); download.type = "button";
  download.addEventListener("click", () => window.Wayfarer.download(plan, `itinerary-${id}.json`));
  const print = planNode("button", "Print viewed plan", "quiet"); print.type = "button"; print.addEventListener("click", () => window.print());
  exports.append(download, print); planEl("plan-output").append(exports);
}
planEl("plan-trip-id").addEventListener("input", () => {
  window.Wayfarer?.setTrip(null);
  resetHistory();
  planTrip = null; planEl("plan-confirm").checked = false; planEl("plan-trip").hidden = true;
  planEl("plan-output").hidden = true; planEl("plan-output").replaceChildren(); planControls(); planStatus("Load the trip before generating.");
});
planEl("plan-confirm").addEventListener("change", planControls);
planEl("plan-load-form").addEventListener("submit", async event => {
  event.preventDefault(); if (planBusy) return;
  const id = planEl("plan-trip-id").value.trim();
  if (!planUuid(id)) { planStatus("Enter a complete trip UUID.", true); return; }
  planBusy = true; planTrip = null; planEl("plan-confirm").checked = false; planControls();
  window.Wayfarer?.setTrip(null);
  resetHistory();
  planEl("plan-trip").hidden = true; planEl("plan-output").hidden = true; planEl("plan-output").replaceChildren(); planStatus("Loading saved trip and latest plan…");
  try {
    const trip = await planRequest(`/api/v1/trips/${id}`);
    if (trip.id?.toLowerCase() !== id.toLowerCase()) throw new Error("Unexpected trip response.");
    planTrip = trip; planEl("plan-trip-summary").textContent = `${trip.destination} · ${trip.startDate} to ${trip.endDate} · ${trip.travelers} travelers`;
    window.Wayfarer?.setTrip(trip);
    planEl("plan-trip").hidden = false;
    planEl("plan-location-link").href = `/location.html?tripId=${encodeURIComponent(id)}`; planEl("plan-chat-link").href = `/?tripId=${encodeURIComponent(id)}`;
    try { showPlan(await planRequest(`/api/v1/trips/${id}/itinerary`), id); planStatus("Latest saved plan loaded. No AI call or weather refresh was made."); }
    catch (error) { if (error.status !== 404) throw error; planStatus("This trip has no saved itinerary yet. You can explicitly generate one."); }
  } catch (error) {
    planTrip = null; // Reload after an unexpected read failure before allowing a write.
    planStatus(error instanceof TypeError ? "Could not reach the app. Try loading again; no generation was requested." : error.message, true);
  } finally { planBusy = false; planControls(); }
});
planEl("plan-generate").addEventListener("click", async () => {
  if (planBusy || !planTrip || !planEl("plan-confirm").checked) return;
  const id = planTrip.id; planBusy = true; planControls(); planStatus("Generating and validating a new version with Ollama… No automatic retries. Closing the page does not cancel server work.");
  try {
    showPlan(await planRequest(`/api/v1/trips/${encodeURIComponent(id)}/itinerary`, "POST"), id, "Newly generated saved plan");
    resetHistory();
    planStatus("New itinerary version saved. Older versions are retained; this page shows the new result.");
  } catch (error) {
    const detail = error.status === 404 || error.status === 405 ? "Generation is unavailable. Check the trip exists and start the app with the ai profile."
      : error instanceof TypeError ? "Connection lost. A version may have been saved; load the latest plan before trying again." : error.message;
    planStatus(`${detail} Any displayed plan is the previous successful result, not a new generation.`, true);
  } finally { planBusy = false; planEl("plan-confirm").checked = false; planControls(); }
});

async function loadHistory(older = false) {
  if (planBusy || !planTrip || (older && historyCursor === null)) return;
  const id = planTrip.id;
  const cursor = older ? historyCursor : null;
  planBusy = true; planControls(); historyStatus("Loading saved version metadata…");
  try {
    const page = await planRequest(`/api/v1/trips/${encodeURIComponent(id)}/itinerary/versions?limit=10${cursor === null ? "" : `&beforeId=${cursor}`}`);
    if (!Array.isArray(page.versions) || page.versions.some(entry => !Number.isSafeInteger(entry.id) || entry.id < 1 || typeof entry.createdAt !== "string")
        || (page.nextBeforeId !== null && (!Number.isSafeInteger(page.nextBeforeId) || page.nextBeforeId < 1 || (cursor !== null && page.nextBeforeId >= cursor)))) {
      throw new Error("Unexpected history response. Refresh history to try again.");
    }
    if (!older) { historyIds.clear(); planEl("history-list").replaceChildren(); }
    for (const entry of page.versions) {
      if (historyIds.has(entry.id)) continue;
      historyIds.add(entry.id);
      const row = planNode("div", "", "history-row");
      row.append(planNode("p", `Saved ID ${entry.id} · ${entry.createdAt}`), planNode("p", `Activity estimate: ${entry.estimatedActivityCostTotal} ${entry.currency}`, "hint"));
      const open = planNode("button", `View saved ID ${entry.id}`); open.type = "button";
      open.addEventListener("click", () => openVersion(id, entry.id)); row.append(open); planEl("history-list").append(row);
    }
    historyCursor = page.nextBeforeId; planEl("history-more").hidden = historyCursor === null;
    historyStatus(historyIds.size ? `${historyIds.size} saved versions listed.${historyCursor === null ? " No older versions in this listing." : " Load older versions for more."}` : "No saved versions yet.");
  } catch (error) { historyStatus(error instanceof TypeError ? "Could not load history. Existing results are unchanged; retry when connected." : error.message, true); }
  finally { planBusy = false; planControls(); }
}
async function openVersion(id, versionId) {
  if (planBusy || !planTrip || planTrip.id !== id) return;
  planBusy = true; planEl("plan-confirm").checked = false; planControls(); historyStatus(`Loading saved ID ${versionId}…`);
  try {
    const version = await planRequest(`/api/v1/trips/${encodeURIComponent(id)}/itinerary/versions/${versionId}`);
    if (version.id !== versionId || typeof version.createdAt !== "string") throw new Error("Unexpected version response.");
    showPlan(version.itinerary, id, `Viewing saved ID ${version.id} · Created ${version.createdAt}`);
    planStatus("Viewing a saved snapshot. The latest version and trip are unchanged; no AI or weather refresh was requested.");
    historyStatus(`Opened saved ID ${version.id}. Use Load trip & latest plan to return to the latest.`);
  } catch (error) { historyStatus(`${error instanceof TypeError ? "Could not reach the app." : error.message} The displayed plan is unchanged.`, true); }
  finally { planBusy = false; planControls(); }
}
planEl("history-refresh").addEventListener("click", () => loadHistory());
planEl("history-more").addEventListener("click", () => loadHistory(true));
