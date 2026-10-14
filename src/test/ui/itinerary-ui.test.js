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
    get("plan-trip-id").value="another"; get("plan-trip-id").dispatchEvent(new Event("input"));
    check(get("plan-output").hidden && get("plan-confirm").disabled && get("plan-generate").disabled,"changing trip clears old context");
    get("plan-trip-id").value=id; window.fetch=async()=>response(404,{detail:"Trip not found"}); load(); await settle();
    check(get("plan-status").textContent.includes("not found") && get("plan-confirm").disabled,"missing trip cannot generate");
    out.textContent=`PASS: ${checks} browser assertions`;
  } catch(error) {out.textContent=`FAIL: ${error.message}`;}
})();
