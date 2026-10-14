"use strict";
(async()=>{
  const output=document.createElement("pre");output.id="test-result";document.body.append(output);let count=0;
  const check=(ok,text)=>{if(!ok)throw new Error(text);count++;};const el=id=>document.getElementById(id);const settle=()=>new Promise(resolve=>setTimeout(resolve,10));
  const id="e99200d3-6d19-4578-ac4f-20cef94fe41c";
  const trip={id,destination:"Jaipur",startDate:"2099-10-12",endDate:"2099-10-13"};
  const forecast={tripId:id,location:{name:"<img src=x onerror=window.injected=true>",country:"India"},timezone:"Asia/Kolkata",fetchedAt:"2099-10-12T01:00:00Z",days:[{date:"2099-10-12",minimumTemperatureCelsius:null,maximumTemperatureCelsius:30,precipitationMm:0}],warnings:["Forecasts change"],attribution:"Open-Meteo",sourceUrl:"https://open-meteo.com/"};
  let calls=[],weatherStatus=200,missingTrip=false;
  const response=(status,data)=>({status,ok:status===200,json:async()=>data});
  window.fetch=async(url,options)=>{calls.push({url,options});if(missingTrip)return response(404,{detail:"Trip not found"});return url.endsWith("/weather")?response(weatherStatus,weatherStatus===200?forecast:weatherStatus===422?{detail:"Dates outside supported window",code:"DATES_OUTSIDE_FORECAST_WINDOW",context:{timezone:"Asia/Kolkata",forecastEndDate:"2099-10-27"}}:{detail:"Unavailable"}):response(200,trip);};
  const submit=()=>el("weather-form").dispatchEvent(new Event("submit",{cancelable:true}));
  try{
    check(calls.length===0,"no automatic forecast call");check(document.documentElement.scrollWidth<=window.innerWidth,"no horizontal overflow");
    el("weather-id").value="bad";submit();check(calls.length===0,"invalid UUID rejected");
    el("weather-id").value=id;submit();submit();await settle();check(calls.length===2&&calls.every(c=>!c.options),"trip and forecast GET only, duplicates blocked");
    check(!el("weather-result").hidden&&el("weather-result").textContent.includes("30 °C"),"forecast rendered");
    check(el("weather-result").textContent.includes("Low: Unavailable")&&el("weather-result").textContent.includes("0 mm"),"missing readings distinct from zero");
    check(!el("weather-result").querySelector("img")&&!window.injected,"provider HTML safe");
    check(el("weather-result").textContent.includes("Open-Meteo")&&el("weather-result").textContent.includes("2099-10-12T01:00:00Z"),"attribution and fetch time preserved");
    check(el("weather-trip").querySelector("a").href.includes(id),"location correction link");
    weatherStatus=422;submit();await settle();check(el("weather-status").textContent.includes("Dates outside")&&el("weather-result").textContent.includes("DATES_OUTSIDE_FORECAST_WINDOW")&&!el("weather-result").textContent.includes("30 °C"),"typed error replaces stale forecast");
    weatherStatus=404;submit();await settle();check(el("weather-status").textContent.includes("no selected location")&&el("weather-result").hidden,"missing selection handled");
    weatherStatus=502;submit();await settle();check(el("weather-status").textContent.includes("Unavailable")&&!el("weather-load").disabled,"provider error recovery");
    missingTrip=true;submit();await settle();check(el("weather-status").textContent.includes("Trip not found")&&el("weather-trip").hidden,"missing trip distinct from missing location");
    window.fetch=async()=>{throw new TypeError("network");};submit();await settle();check(el("weather-status").textContent.includes("Could not reach")&&el("weather-result").hidden,"network failure clears forecast");
    output.textContent=`PASS: ${count} browser assertions`;
  }catch(error){output.textContent=`FAIL: ${error.message}`;}
})();
