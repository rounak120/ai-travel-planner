"use strict";
(() => {
  const {node,validId,request,setTrip,link}=window.Wayfarer;const el=id=>document.getElementById(id);let busy=false;
  const linked=new URLSearchParams(location.search).get("tripId");if(validId(linked))el("weather-id").value=linked;
  function status(text,error=false){el("weather-status").textContent=text;el("weather-status").classList.toggle("error",error);}
  el("weather-id").addEventListener("input",()=>{el("weather-result").hidden=true;el("weather-trip").hidden=true;setTrip(null);});
  const reading=(value,unit)=>typeof value==="number"&&Number.isFinite(value)?`${value} ${unit}`:"Unavailable";
  el("weather-form").addEventListener("submit",async event=>{
    event.preventDefault();if(busy)return;const id=el("weather-id").value.trim();if(!validId(id)){status("Enter a complete trip UUID.",true);return;}
    busy=true;el("weather-load").disabled=true;el("weather-id").disabled=true;el("weather-result").hidden=true;el("weather-result").replaceChildren();el("weather-trip").hidden=true;status("Loading trip and requesting its forecast…");
    let tripLoaded=false;
    try{
      const trip=await request(`/api/v1/trips/${id}`);if(trip.id?.toLowerCase()!==id.toLowerCase())throw new Error("Unexpected trip response.");setTrip(trip);tripLoaded=true;
      el("weather-trip").replaceChildren(node("h3",`${trip.destination} · ${trip.startDate} to ${trip.endDate}`),link("/location.html","Review or select location",id));el("weather-trip").hidden=false;
      const weather=await request(`/api/v1/trips/${id}/weather`);
      if(weather.tripId?.toLowerCase()!==id.toLowerCase()||!Array.isArray(weather.days)||!weather.location)throw new Error("Unexpected weather response.");
      const fragment=document.createDocumentFragment();fragment.append(node("h2",`${weather.location.name}, ${weather.location.country||"Country unavailable"}`),node("p",`Timezone: ${weather.timezone} · Fetched: ${weather.fetchedAt}`,"hint"));
      const days=node("div","","weather-grid");for(const day of weather.days){const card=node("article","","forecast-card");card.append(node("h3",day.date),node("p",reading(day.maximumTemperatureCelsius,"°C"),"forecast-temperature"),node("p",`Low: ${reading(day.minimumTemperatureCelsius,"°C")}`),node("p",`Precipitation: ${reading(day.precipitationMm,"mm")}`));days.append(card);}fragment.append(days);
      const warnings=node("ul","","plan-warnings");for(const warning of weather.warnings||[])warnings.append(node("li",warning));fragment.append(warnings,node("p",`${weather.attribution||""} ${weather.sourceUrl||""}`,"source-note"));
      const evidence=node("details","","evidence");evidence.append(node("summary","Full forecast data"),node("pre",JSON.stringify(weather,null,2)));fragment.append(evidence);el("weather-result").replaceChildren(fragment);el("weather-result").hidden=false;status("Forecast received. Recheck near departure; no AI was used.");
    }catch(error){
      if(!tripLoaded)setTrip(null);
      status(error instanceof TypeError?"Could not reach the app. No forecast is shown; try again when connected.":error.message,true);
      if(error.data?.context){el("weather-result").replaceChildren(node("h3",error.data.code||"Forecast unavailable"),node("pre",JSON.stringify(error.data.context,null,2)));el("weather-result").hidden=false;}
      else if(tripLoaded&&error.status===404)status("This trip has no selected location. Choose a location using the link above, then check again.",true);
    }finally{busy=false;el("weather-load").disabled=false;el("weather-id").disabled=false;}
  });
})();
