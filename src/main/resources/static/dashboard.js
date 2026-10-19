"use strict";
(() => {
  const {node,validId,request,setTrip,shortcuts,remember,forget,link}=window.Wayfarer;
  const el=id=>document.getElementById(id); let busy=false;
  const linked=new URLSearchParams(location.search).get("tripId"); if(validId(linked)) el("overview-id").value=linked;
  function status(text,error=false){el("overview-status").textContent=text;el("overview-status").classList.toggle("error",error);}
  function renderShortcuts(){
    el("overview-shortcuts").replaceChildren(); const records=shortcuts();
    if(!records.length){el("overview-shortcuts").append(node("p","No shortcuts yet. Open a trip and choose “Save browser shortcut” to add one.","empty-state"));return;}
    for(const trip of records){
      const card=node("article","","shortcut-card");card.append(node("h3",trip.destination),node("p",trip.id,"hint"));
      const open=node("button","Open trip","secondary");open.type="button";open.addEventListener("click",()=>{if(busy)return;el("overview-id").value=trip.id;el("overview-id").dispatchEvent(new Event("input"));el("overview-form").requestSubmit();});
      const remove=node("button","Remove shortcut","quiet");remove.type="button";remove.addEventListener("click",()=>{try{forget(trip.id);renderShortcuts();}catch(error){status(error.message,true);}});
      card.append(open,remove);el("overview-shortcuts").append(card);
    }
  }
  el("overview-id").addEventListener("input",()=>{el("overview-trip").hidden=true;el("overview-trip").replaceChildren();setTrip(null);});
  el("overview-form").addEventListener("submit",async event=>{
    event.preventDefault();if(busy)return;const id=el("overview-id").value.trim();if(!validId(id)){status("Enter a complete trip UUID.",true);return;}
    busy=true;el("overview-load").disabled=true;el("overview-id").disabled=true;el("overview-trip").hidden=true;status("Loading saved trip…");
    try{
      const trip=await request(`/api/v1/trips/${id}`);if(trip.id?.toLowerCase()!==id.toLowerCase())throw new Error("Unexpected trip response.");setTrip(trip);
      const content=document.createDocumentFragment();content.append(node("h2",trip.destination));
      const metrics=node("dl","","trip-metrics");for(const [label,value] of [["Dates",`${trip.startDate} → ${trip.endDate}`],["Travelers",trip.travelers],["Budget",`${trip.budget} ${trip.currency}`]]){const item=node("div","");item.append(node("dt",label),node("dd",String(value)));metrics.append(item);}content.append(metrics,node("p",`Interests: ${(trip.interests||[]).join(", ") || "None specified"}`));
      const actions=node("div","","link-grid");for(const [path,text] of [["/location.html","Choose location"],["/weather.html","Check weather"],["/","Ask assistant"],["/itinerary.html","View itineraries"]])actions.append(link(path,text,trip.id));content.append(actions);
      const save=node("button","Save browser shortcut","secondary");save.type="button";save.addEventListener("click",()=>{try{remember(trip);renderShortcuts();status("Shortcut saved on this browser. No trip data was changed.");}catch(error){status(error.message,true);}});
      const exportButton=node("button","Download trip JSON","quiet");exportButton.type="button";exportButton.addEventListener("click",()=>window.Wayfarer.download(trip,`trip-${trip.id}.json`));content.append(save,exportButton);
      el("overview-trip").replaceChildren(content);el("overview-trip").hidden=false;status("Trip loaded. Choose the next step below.");
      const edit=node("a","Edit trip","action-link");edit.href=`/edit.html?tripId=${encodeURIComponent(trip.id)}`;
      const remove=node("button","Delete trip","quiet");remove.type="button";
      remove.addEventListener("click",async()=>{
        if(busy || !window.confirm(`Permanently delete ${trip.destination}, its selected location and ALL saved itineraries? This cannot be undone.`))return;
        busy=true;remove.disabled=true;el("overview-load").disabled=true;el("overview-id").disabled=true;
        try {
          await request(`/api/v1/trips/${trip.id}`,{method:"DELETE"});
          try{forget(trip.id);renderShortcuts();}catch{/* The server deletion succeeded even if local storage is unavailable. */}
          el("overview-trip").hidden=true;el("overview-trip").replaceChildren();setTrip(null);status("Trip and associated history deleted.");
          await loadTrips(0);
        }catch(error){status(error.message,true);}finally{busy=false;remove.disabled=false;el("overview-load").disabled=false;el("overview-id").disabled=false;}
      });
      el("overview-trip").append(edit,remove);
    }catch(error){setTrip(null);status(error instanceof TypeError?"Could not reach the app. Check that it is running.":error.message,true);}
    finally{busy=false;el("overview-load").disabled=false;el("overview-id").disabled=false;}
  });renderShortcuts();

  // Library browsing is independent of the currently opened trip and local shortcuts.
  let listBusy=false, currentPage=null, hasNext=false;
  function listControls() {
    el("trips-refresh").disabled=listBusy;
    el("trips-prev").disabled=listBusy || currentPage===null || currentPage===0;
    el("trips-next").disabled=listBusy || !hasNext;
    el("trips-list").setAttribute("aria-busy",String(listBusy));
  }
  async function loadTrips(page) {
    if(listBusy)return;
    listBusy=true;listControls();
    el("trips-list-status").textContent="Loading saved trips…";
    el("trips-list-status").classList.remove("error");
    try {
      const data=await request(`/api/v1/trips?page=${page}&size=10`);
      if(!Array.isArray(data.trips) || data.page!==page || data.size!==10 ||
          !Number.isInteger(data.totalPages) || data.totalPages<0 ||
          !Number.isInteger(data.totalElements) || data.totalElements<0 ||
          typeof data.hasNext!=="boolean" || data.trips.some(t=>!t || !validId(t.id))) {
        throw new Error("Unexpected trip list response.");
      }
      const cards=document.createDocumentFragment();
      for(const trip of data.trips) {
        const card=node("article","","shortcut-card");
        card.append(node("h3",trip.destination),node("p",`${trip.startDate} → ${trip.endDate}`),
          node("p",`${trip.travelers} travelers · ${trip.budget} ${trip.currency}`));
        const open=node("button","Open trip","secondary");open.type="button";
        open.addEventListener("click",()=>{
          if(busy)return;
          el("overview-id").value=trip.id;el("overview-id").dispatchEvent(new Event("input"));
          el("overview-form").requestSubmit();el("overview-id").focus();
        });
        card.append(open);cards.append(card);
      }
      el("trips-list").replaceChildren(cards);currentPage=page;hasNext=data.hasNext;
      el("trips-page").textContent=data.trips.length ? `Page ${page+1} of ${data.totalPages}` : "";
      el("trips-list-status").textContent=data.trips.length ? `${data.totalElements} saved trips. Newest first.`
        : page===0 ? "No saved trips yet. Create your first trip above." : "No trips on this page. Go back or refresh the list.";
    } catch(error) {
      el("trips-list-status").classList.add("error");
      el("trips-list-status").textContent=(error instanceof TypeError ? "Could not reach the app." : error.message)
        + (currentPage===null ? " Try loading again." : " Previously loaded trips remain shown. Retry or refresh.");
    } finally { listBusy=false;listControls(); }
  }
  el("trips-refresh").addEventListener("click",()=>loadTrips(0));
  el("trips-prev").addEventListener("click",()=>{if(currentPage>0)loadTrips(currentPage-1);});
  el("trips-next").addEventListener("click",()=>{if(hasNext)loadTrips(currentPage+1);});
})();
