"use strict";
(() => {
  const validId = id => typeof id === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
  const node = (tag, text, className) => { const el = document.createElement(tag); el.textContent = text; if (className) el.className = className; return el; };
  const routes = [["/dashboard.html","Overview","01"],["/trips.html","Create a trip","02"],["/location.html","Choose location","03"],["/weather.html","Trip weather","04"],["/","AI assistant","05"],["/itinerary.html","Itineraries","06"]];
  const key = "wayfarer.shortcuts.v1";
  let current = new URLSearchParams(location.search).get("tripId");
  if (!validId(current)) current = null;
  function shortcuts() {
    try { const data = JSON.parse(localStorage.getItem(key) || "[]"); return Array.isArray(data) ? data.filter(t => validId(t.id) && typeof t.destination === "string").slice(0,10) : []; }
    catch { return []; }
  }
  function remember(trip) {
    if (!validId(trip.id)) throw new Error("Invalid trip ID.");
    const records = [{id:trip.id,destination:String(trip.destination).slice(0,200)},...shortcuts().filter(t=>t.id!==trip.id)].slice(0,10);
    try { localStorage.setItem(key,JSON.stringify(records)); } catch { throw new Error("Browser storage is unavailable. Your trip is still saved on the server."); }
  }
  function forget(id) { try { localStorage.setItem(key,JSON.stringify(shortcuts().filter(t=>t.id!==id))); } catch { throw new Error("Could not update browser shortcuts."); } }
  function setTrip(trip, updateUrl = true) {
    current = trip && validId(trip.id) ? trip.id : null;
    document.querySelectorAll("[data-app-route]").forEach(link => {
      const path = link.dataset.appRoute;
      link.href = path + (current && path !== "/trips.html" ? `?tripId=${encodeURIComponent(current)}` : "");
    });
    const context = document.getElementById("workspace-trip");
    if (context) context.textContent = current ? `Trip ${current.slice(0,8)}…` : "No trip selected";
    if (updateUrl) history.replaceState(null,"",location.pathname + (current ? `?tripId=${encodeURIComponent(current)}` : ""));
  }
  async function request(url, options) {
    const response = await fetch(url, options);
    const data = await response.json().catch(()=>null);
    if (!response.ok) { const error = new Error(data?.detail || `Request failed (HTTP ${response.status}).`); error.status = response.status; error.data = data; throw error; }
    if (!data) throw new Error("Unexpected response from the app.");
    return data;
  }
  function link(path, text, tripId = current) { const el=node("a",text,"action-link"); el.href=path+(validId(tripId)?`?tripId=${encodeURIComponent(tripId)}`:""); return el; }
  function download(data, filename) {
    const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:"application/json"}));
    const a=node("a",""); a.href=url; a.download=filename; document.body.append(a); a.click(); a.remove(); setTimeout(()=>URL.revokeObjectURL(url),1000);
  }
  window.Wayfarer = {validId,node,request,setTrip,shortcuts,remember,forget,link,download};
  const main=document.querySelector("main"); main.id="main-content";
  document.body.classList.add("app-frame");
  const skip=node("a","Skip to content","skip-link"); skip.href="#main-content"; document.body.prepend(skip);
  const sidebar=node("nav","","app-sidebar"); sidebar.setAttribute("aria-label","Main navigation");
  const brand=node("a","W / WAYFARER","app-brand"); brand.href="/dashboard.html"; sidebar.append(brand,node("p","YOUR TRAVEL WORKSPACE","nav-eyebrow"));
  const routeList=node("div","","route-list");
  for (const [path,label,index] of routes) {
    const a=node("a","","app-nav-link"); a.dataset.appRoute=path; a.append(node("span",index,"nav-index"),node("span",label));
    if(location.pathname===path || (location.pathname==="/index.html" && path==="/")) a.setAttribute("aria-current","page"); routeList.append(a);
  }
  sidebar.append(routeList);
  const note=node("div","","sidebar-note"); note.append(node("span","LOCAL & PERSONAL","nav-eyebrow"),node("p","Plan with intention."),node("small","Local AI. Real source data. Your choices stay yours.")); sidebar.append(note); document.body.prepend(sidebar);
  const header=main.querySelector("header");
  if(header) {
    const title=routes.find(([path])=>path===location.pathname)?.[1] || "AI assistant";
    const chip=node("span","","workspace-trip"); chip.id="workspace-trip";
    header.replaceChildren(node("span",`Workspace / ${title}`,"breadcrumb"),chip,node("span","LOCAL DEMO","badge"));
  }
  setTrip(current ? {id:current} : null,false);
})();
