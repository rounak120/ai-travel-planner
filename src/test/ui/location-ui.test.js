"use strict";
(async () => {
  const output = document.createElement("pre"); output.id = "test-result"; document.body.append(output);
  let checks = 0;
  function check(ok, description) { if (!ok) throw new Error(description); checks++; }
  const get = id => document.getElementById(id);
  const submit = id => get(id).dispatchEvent(new Event("submit", {cancelable:true}));
  const settle = () => new Promise(resolve => setTimeout(resolve, 10));
  const id = "e99200d3-6d19-4578-ac4f-20cef94fe41c";
  const trip = {id, destination:"Jaipur", startDate:"2099-10-12", endDate:"2099-10-13"};
  const place = {id:1269515, name:"<img src=x onerror=window.injected=true>", country:"India", region:"Rajasthan", latitude:26.91, longitude:75.78, timezone:"Asia/Kolkata"};
  const selection = {tripId:id, place, attribution:"GeoNames", sourceUrl:"https://open-meteo.com/"};
  const response = (status, data) => ({status, ok:status===200, json:async()=>data});
  let requests = [], savedLocation = null, empty = false, searchFailure = false, saveFailure = false;
  window.fetch = async (url, options = {}) => {
    requests.push({url, options});
    if (options.method === "PUT") {
      if (saveFailure) return response(422, {detail:"Search again and choose a returned ID."});
      savedLocation = selection; return response(200, selection);
    }
    if (url.startsWith("/api/v1/destinations?")) return searchFailure ? response(502, {detail:"Search unavailable"})
      : response(200, {results:empty ? [] : [place, {...place,id:2,country:"Bangladesh",timezone:null}],attribution:"GeoNames",sourceUrl:"https://open-meteo.com/"});
    if (url.endsWith("/location")) return savedLocation ? response(200,savedLocation) : response(404,{detail:"No selected location"});
    return response(200,trip);
  };
  const search = async () => { submit("search-location-form"); await settle(); };
  try {
    check(requests.length === 0 && get("search-location").disabled, "no automatic request or search before loading");
    check(document.documentElement.scrollWidth <= window.innerWidth, "no horizontal overflow");
    get("location-trip-id").value = "bad"; submit("load-trip-form");
    check(requests.length === 0 && get("location-status").textContent.includes("UUID"), "invalid UUID rejected");
    get("location-trip-id").value = id; submit("load-trip-form"); submit("load-trip-form"); await settle();
    check(requests.length === 2 && requests.every(r=>!r.options.method), "load is two reads and duplicate load blocked");
    check(get("current-location").textContent.includes("No selected") && !get("search-location").disabled, "missing location is shown");
    await search();
    check(document.querySelectorAll("input[name=candidate]").length === 2 && get("save-location").disabled, "results not auto-selected");
    check(!document.querySelector("#location-candidates img") && !window.injected, "provider HTML stays text");
    check(get("location-candidates").textContent.includes("India") && get("location-candidates").textContent.includes("Bangladesh") && get("location-candidates").textContent.includes("Unavailable"), "country region timezone shown");
    document.querySelector("input[name=candidate]").click(); get("save-location").click(); get("save-location").click(); await settle();
    const writes = requests.filter(r=>r.options.method === "PUT");
    check(writes.length === 1 && writes[0].url === `/api/v1/trips/${id}/location`, "one explicit write to loaded trip");
    check(JSON.stringify(JSON.parse(writes[0].options.body)) === JSON.stringify({searchName:"Jaipur",placeId:1269515}), "save sends captured query and ID only");
    check(get("current-location").textContent.includes("Asia/Kolkata") && get("save-location").disabled && get("location-results").hidden, "saved display and duplicate prevention");
    check(get("location-chat").getAttribute("href") === `/?tripId=${id}`, "chat handoff");
    submit("load-trip-form"); await settle(); check(get("current-attribution").textContent.includes("GeoNames"), "existing selection loaded");
    await search(); document.querySelector("input[name=candidate]").click();
    get("location-query").value = "Delhi"; get("location-query").dispatchEvent(new Event("input"));
    check(get("save-location").disabled && get("location-results").hidden, "query edit invalidates stale results");
    empty = true; await search(); check(get("location-status").textContent.includes("No matches") && get("save-location").disabled, "empty search");
    empty = false; searchFailure = true; await search(); check(get("location-status").textContent.includes("unavailable") && !get("search-location").disabled, "provider failure restores controls");
    searchFailure = false; await search(); document.querySelector("input[name=candidate]").click(); saveFailure = true;
    get("save-location").click(); await settle();
    check(get("location-status").textContent.includes("Search again") && get("save-location").disabled && get("current-location").textContent.includes("Asia/Kolkata"), "failed save preserves displayed selection and clears stale choices");
    saveFailure = false; await search(); document.querySelector("input[name=candidate]").click();
    window.fetch = async () => { throw new TypeError("network"); };
    get("save-location").click(); await settle();
    check(get("location-status").textContent.includes("may have been saved") && get("save-location").disabled && !get("load-trip").disabled, "ambiguous save failure requires reload and has no automatic retry");
    get("location-trip-id").value = "another"; get("location-trip-id").dispatchEvent(new Event("input"));
    check(get("search-location").disabled && get("trip-context").hidden && get("location-chat").hidden, "trip edit invalidates context");
    window.fetch = async () => response(404,{detail:"Trip not found"}); get("location-trip-id").value = id; submit("load-trip-form"); await settle();
    check(get("location-status").textContent.includes("not found") && get("search-location").disabled, "missing trip cannot search");
    output.textContent = `PASS: ${checks} browser assertions`;
  } catch (error) { output.textContent = `FAIL: ${error.message}`; }
})();
