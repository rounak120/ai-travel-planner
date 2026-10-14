"use strict";
(async () => {
  const out = document.createElement("pre"); out.id = "test-result"; document.body.append(out);
  let checks = 0;
  const check = (ok, text) => { if (!ok) throw new Error(text); checks++; };
  const get = id => document.getElementById(id);
  const settle = () => new Promise(resolve => setTimeout(resolve, 10));
  const load = () => get("plan-load-form").dispatchEvent(new Event("submit", {cancelable:true}));
  const confirm = () => { get("plan-confirm").checked = true; get("plan-confirm").dispatchEvent(new Event("change")); };
  const id = "e99200d3-6d19-4578-ac4f-20cef94fe41c";
  const trip = {id, destination:"Jaipur", startDate:"2099-10-12", endDate:"2099-10-13", travelers:2};
  const draft = {tripId:id,destination:"Jaipur",currency:"INR",tripBudget:5000,estimatedActivityCostTotal:100,
    days:[{dayNumber:1,date:"2099-10-12",estimatedActivityCost:100,activities:[{title:"<img src=x onerror=window.injected=true>",description:"Explore the city",estimatedCost:100}]}],
    warnings:["Check opening hours"],selectedLocation:null,weatherContext:null};
  const response = (status,data) => ({status,ok:status===200,json:async()=>data});
  let calls = [], saved = null, postStatus = 200, getStatus = 200, waitPost = false, finish;
  window.fetch = async (url, options = {}) => {
    calls.push({url,options});
    if (options.method === "POST") {
      if (waitPost) return new Promise(resolve=>{finish=resolve;});
      return response(postStatus, postStatus===200 ? draft : {detail:"Generation failed safely"});
    }
    if (getStatus !== 200) return response(getStatus,{detail:"Read failed"});
    return url.endsWith("/itinerary") ? (saved ? response(200,saved) : response(404,{detail:"No saved plan"})) : response(200,trip);
  };
  try {
    check(calls.length===0 && get("plan-generate").disabled,"no automatic calls or generation");
    check(document.documentElement.scrollWidth<=window.innerWidth,"no horizontal overflow");
    get("plan-trip-id").value="bad"; load(); check(calls.length===0,"invalid UUID blocked");
    get("plan-trip-id").value=id; load(); load(); await settle();
    check(calls.length===2 && calls.every(c=>c.options.method==="GET"),"load only reads and prevents duplicates");
    check(get("plan-status").textContent.includes("no saved") && !get("plan-confirm").disabled && get("plan-generate").disabled,"missing plan needs explicit consent");
    saved=draft; load(); await settle();
    check(!get("plan-output").hidden && get("plan-output").textContent.includes("100 INR"),"saved days and costs rendered");
    check(!get("plan-output").querySelector("img") && !window.injected,"AI content is plain text");
    check(get("plan-output").textContent.includes("No weather snapshot") && get("plan-output").textContent.includes("Check opening hours"),"legacy snapshots and warnings");
    check(get("plan-output").querySelector("pre").textContent.includes('"tripId"'),"full evidence retained");
    waitPost=true; confirm(); get("plan-generate").click(); get("plan-generate").click();
    check(calls.filter(c=>c.options.method==="POST").length===1 && get("plan-load").disabled && get("plan-trip-id").disabled,"generation is one explicit POST and locks trip");
    finish(response(200,{...draft,weatherContext:{status:"AVAILABLE",message:"Stored forecast",forecast:{fetchedAt:"2099-10-10T10:00:00Z"}}})); await settle(); waitPost=false;
    check(get("plan-status").textContent.includes("version saved") && !get("plan-confirm").checked && get("plan-generate").disabled,"success resets consent");
    check(get("plan-output").textContent.includes("not a fresh forecast"),"weather snapshot labelled");
    const previous=get("plan-output").textContent;
    for (const code of [502,422,405]) {
      postStatus=code; confirm(); get("plan-generate").click(); await settle();
      check(get("plan-output").textContent===previous && !get("plan-load").disabled && get("plan-generate").disabled,`HTTP ${code} preserves old plan and resets consent`);
    }
    check(get("plan-status").textContent.includes("ai profile"),"missing generation profile explained");
    window.fetch=async()=>{throw new TypeError("network");}; confirm(); get("plan-generate").click(); await settle();
    check(get("plan-status").textContent.includes("may have been saved") && get("plan-output").textContent===previous,"ambiguous network outcome and old plan preserved");
    const historyCalls = [];
    let versionFailure = false, historyFailure = false, emptyHistory = false;
    const entry = n => ({id:n,createdAt:"2099-10-10T10:00:00Z",currency:"INR",estimatedActivityCostTotal:100});
    window.fetch = async (url, options = {}) => {
      historyCalls.push({url,options});
      if (options.method === "POST") return response(200,draft);
      if (url.includes("/versions?")) {
        if (historyFailure) throw new TypeError("network");
        if (emptyHistory) return response(200,{versions:[],nextBeforeId:null});
        return url.includes("beforeId=20") ? response(200,{versions:[entry(10)],nextBeforeId:null}) : response(200,{versions:[entry(30),entry(20)],nextBeforeId:20});
      }
      if (url.endsWith("/versions/10")) return versionFailure ? response(404,{detail:"Version not found"}) : response(200,{id:10,createdAt:"2099-10-09T10:00:00Z",itinerary:{...draft,destination:"Older destination",selectedLocation:{place:{name:"Old place",country:"India",region:"Rajasthan",timezone:"Asia/Kolkata"}},weatherContext:{status:"UNSUPPORTED",message:"Saved unsupported weather"}}});
      return response(200,url.endsWith("/itinerary") ? draft : trip);
    };
    check(get("history-list").children.length===0,"history is not loaded automatically");
    get("history-refresh").click(); get("history-refresh").click();
    check(get("plan-generate").disabled && get("plan-trip-id").disabled,"history locks concurrent writes and trip edits");
    await settle();
    check(historyCalls.length===1 && historyCalls[0].url.endsWith("/versions?limit=10") && get("history-list").children.length===2,"one explicit first history page");
    get("history-more").click(); await settle();
    check(historyCalls.at(-1).url.endsWith("beforeId=20") && get("history-list").children.length===3 && get("history-more").hidden,"cursor pagination appends older entries and detects end");
    get("history-list").querySelectorAll("button")[2].click(); await settle();
    check(get("plan-output").textContent.includes("Viewing saved ID 10") && get("plan-output").textContent.includes("2099-10-09T10:00:00Z"),"version ID and creation time identify displayed snapshot");
    check(get("plan-output").textContent.includes("Old place") && get("plan-output").textContent.includes("Saved unsupported weather"),"older location and weather context stay intact");
    check(historyCalls.every(c=>c.options.method==="GET"),"browsing never writes or generates");
    const oldSnapshot=get("plan-output").textContent;
    versionFailure=true; get("history-list").querySelectorAll("button")[2].click(); await settle();
    check(get("history-status").textContent.includes("not found") && get("plan-output").textContent===oldSnapshot,"missing version preserves displayed snapshot");
    historyFailure=true; get("history-refresh").click(); await settle();
    check(get("history-list").children.length===3 && get("plan-output").textContent===oldSnapshot,"history network error preserves list and viewed plan");
    historyFailure=false; load(); await settle();
    check(get("plan-output").textContent.includes("Latest saved plan at load time") && get("history-list").children.length===0,"return to latest resets history state");
    emptyHistory=true; get("history-refresh").click(); await settle();
    check(get("history-status").textContent.includes("No saved versions") && get("history-more").hidden,"empty history handled");
    emptyHistory=false; get("history-refresh").click(); await settle(); confirm(); get("plan-generate").click(); await settle();
    check(get("history-list").children.length===0 && get("plan-output").textContent.includes("Newly generated"),"generation invalidates old history list");
    get("plan-trip-id").value="another"; get("plan-trip-id").dispatchEvent(new Event("input"));
    check(get("plan-output").hidden && get("plan-confirm").disabled && get("plan-generate").disabled,"changing trip clears old context");
    check(get("history-list").children.length===0 && get("history-refresh").disabled,"trip edit clears and disables history");
    get("plan-trip-id").value=id; window.fetch=async()=>response(404,{detail:"Trip not found"}); load(); await settle();
    check(get("plan-status").textContent.includes("not found") && get("plan-confirm").disabled,"missing trip cannot generate");
    out.textContent=`PASS: ${checks} browser assertions`;
  } catch(error) {out.textContent=`FAIL: ${error.message}`;}
})();
