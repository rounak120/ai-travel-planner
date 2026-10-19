"use strict";
(() => {
  const el=id=>document.getElementById(id);const id=new URLSearchParams(location.search).get("tripId");let busy=false;
  const fields=["destination","startDate","endDate","travelers","budget","currency"];
  async function run(action){if(busy)return;busy=true;document.querySelectorAll("input,textarea,button").forEach(e=>e.disabled=true);try{await action();}catch(error){el("edit-status").textContent=error.message;}finally{busy=false;document.querySelectorAll("input,textarea,button").forEach(e=>e.disabled=false);}}
  el("edit-load").addEventListener("click",()=>run(async()=>{
    if(!Wayfarer.validId(id))throw new Error("Open Edit trip from a saved trip's overview.");
    const trip=await Wayfarer.request(`/api/v1/trips/${id}`);for(const field of fields)el(field).value=trip[field];el("interests").value=trip.interests.join("\n");
    el("edit-form").hidden=false;Wayfarer.setTrip(trip);el("edit-status").textContent="Loaded. Start date must be today or later when saving.";
  }));
  el("edit-form").addEventListener("submit",event=>{event.preventDefault();run(async()=>{
    const body=Object.fromEntries(fields.map(field=>[field,el(field).value]));body.travelers=Number(body.travelers);body.interests=el("interests").value.split("\n").map(s=>s.trim()).filter(Boolean);
    const trip=await Wayfarer.request(`/api/v1/trips/${id}`,{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
    Wayfarer.setTrip(trip);el("edit-status").textContent="Changes saved. Old itinerary snapshots were not changed.";
  });});
})();
