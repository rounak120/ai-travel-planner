"use strict";
const tripForm = document.querySelector("#trip-form");
const tripStatus = document.querySelector("#trip-status");
const tripErrors = document.querySelector("#trip-errors");
const savedTrip = document.querySelector("#saved-trip");
const field = id => document.getElementById(id);
let saving = false;
let saved = false;
let selectedDestination = null;
let searchSequence = 0;
let searchTimer;
let searchAbort;

function clearDestination() {
  selectedDestination=null;searchSequence++;clearTimeout(searchTimer);searchAbort?.abort();
  field("destination-results").replaceChildren();field("destination-options").hidden=true;
  field("destination-source").textContent="";
}
async function searchDestinations() {
  if(saving||saved)return;
  clearDestination();const sequence=searchSequence;const query=field("destination").value.trim();
  if(query.length<2){field("destination-status").textContent="Type at least two characters to search.";return;}
  searchAbort=new AbortController();field("destination-status").textContent="Searching destinations…";
  try {
    const response=await fetch(`/api/v1/destinations?name=${encodeURIComponent(query)}`,{signal:searchAbort.signal});
    const data=await response.json().catch(()=>null);
    if(sequence!==searchSequence||saving||saved)return;
    if(!response.ok)throw new Error(data?.detail||`Destination search failed (HTTP ${response.status}).`);
    if(!Array.isArray(data?.results))throw new Error("Unexpected destination search response. Try again.");
    for(const place of data.results) {
      if(!place||!Number.isSafeInteger(place.id)||place.id<1||typeof place.name!=="string")throw new Error("Invalid destination result. Search again.");
    }
    for(const place of data.results) {
      const label=document.createElement("label");label.className="location-option";
      const radio=document.createElement("input");radio.type="radio";radio.name="destination-choice";radio.value=String(place.id);
      const text=document.createElement("span");text.textContent=`${place.name} · ${place.region||"Region unavailable"} · ${place.country||"Country unavailable"} · ${place.timezone||"Timezone unavailable"}`;
      radio.addEventListener("change",()=>{if(radio.checked){selectedDestination={searchName:query,placeId:place.id,name:place.name};field("destination-status").textContent=`Selected: ${text.textContent}`;}});
      label.append(radio,text);field("destination-results").append(label);
    }
    field("destination-options").hidden=data.results.length===0;
    field("destination-status").textContent=data.results.length?"Select a destination below before creating your trip.":"No destinations found. Try another spelling or nearby city.";
    field("destination-source").textContent=`${data.attribution||""} ${data.sourceUrl||""}`;
  }catch(error){if(sequence!==searchSequence||error.name==="AbortError")return;field("destination-status").textContent=error instanceof TypeError?"Could not search destinations. Check your connection and try again.":error.message;}
}
field("destination").addEventListener("input",()=>{
  clearDestination();const query=field("destination").value.trim();
  field("destination-status").textContent=query.length<2?"Type at least two characters to search.":"Waiting to search…";
  if(query.length>=2)searchTimer=setTimeout(searchDestinations,400);
});
field("destination-search").addEventListener("click",searchDestinations);

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
  if (!selectedDestination || selectedDestination.searchName!==destination) { reportTrip("Select a destination from the search results before creating your trip.",true);return; }
  if (field("end-date").value < field("start-date").value) { reportTrip("End date must be on or after start date.", true); return; }
  if (interests.length > 20 || interests.some(value => value.length > 80)) { reportTrip("Use at most 20 interests, each at most 80 characters.", true); return; }
  const body = { destination: selectedDestination.name, startDate: field("start-date").value, endDate: field("end-date").value,
    travelers: Number(field("travelers").value), budget: field("budget").value,
    currency: field("currency").value.trim().toUpperCase(), interests };
  saving = true;
  lockTrip(true);
  tripForm.setAttribute("aria-busy", "true");
  reportTrip("Saving your trip…");
  try {
    const response = await fetch("/api/v1/trips/with-location", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({trip:body,location:{searchName:selectedDestination.searchName,placeId:selectedDestination.placeId}}) });
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
    window.Wayfarer?.setTrip(data);
    field("saved-id").value = data.id;
    field("saved-summary").textContent = `${data.destination} · ${data.startDate} to ${data.endDate} · ${data.travelers} travelers`;
    field("open-chat").href = `/?tripId=${encodeURIComponent(data.id)}`;
    field("select-location").href = `/location.html?tripId=${encodeURIComponent(data.id)}`;
    field("open-trip").href = `/api/v1/trips/${encodeURIComponent(data.id)}`;
    savedTrip.hidden = false;
    field("select-location").textContent="Review or change destination ↗";
    reportTrip("Trip and selected destination saved. You can now check weather or generate an itinerary.");
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
  clearDestination();field("destination-status").textContent="Type at least two characters to search.";
  window.Wayfarer?.setTrip(null);
  tripErrors.replaceChildren(); reportTrip("Ready for another trip. The previous trip remains saved."); field("destination").focus();
});
