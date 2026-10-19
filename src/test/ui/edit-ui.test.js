"use strict";
(async()=>{
 const out=document.createElement("pre");out.id="test-result";document.body.append(out);let count=0;
 const check=(condition,text)=>{if(!condition)throw new Error(text);count++;};const el=id=>document.getElementById(id);const settle=()=>new Promise(r=>setTimeout(r,10));
 const id=new URLSearchParams(location.search).get("tripId");const trip={id,destination:"Jaipur",startDate:"2099-10-12",endDate:"2099-10-13",travelers:2,budget:"5000.25",currency:"INR",interests:["food","history"]};let calls=[],finish;
 window.fetch=(url,options)=>{calls.push({url,options});return new Promise(r=>finish=r);};const ok=data=>({ok:true,status:200,json:async()=>data});
 try{
  check(el("edit-form").hidden&&calls.length===0,"edit requires explicit load");
  el("edit-load").click();el("edit-load").click();check(calls.length===1&&el("edit-load").disabled,"busy guard");finish(ok(trip));await settle();
  check(!el("edit-form").hidden&&el("budget").value==="5000.25"&&el("interests").value==="food\nhistory","trip populates form");
  el("destination").value="Delhi";el("edit-form").dispatchEvent(new Event("submit",{cancelable:true}));
  check(calls.at(-1).options.method==="PUT"&&JSON.parse(calls.at(-1).options.body).destination==="Delhi","explicit update");
  check(JSON.parse(calls.at(-1).options.body).budget==="5000.25","money kept decimal string");
  finish(ok({...trip,destination:"Delhi"}));await settle();check(el("edit-status").textContent.includes("Changes saved"),"save confirmation");
  window.fetch=async()=>({ok:false,status:400,json:async()=>({detail:"Invalid trip"})});el("edit-form").dispatchEvent(new Event("submit",{cancelable:true}));await settle();
  check(el("destination").value==="Delhi"&&!el("destination").disabled&&el("edit-status").textContent.includes("Invalid"),"validation preserves input");
  window.fetch=async()=>{throw new TypeError("Network unavailable");};el("edit-form").dispatchEvent(new Event("submit",{cancelable:true}));await settle();check(el("edit-status").textContent.includes("Network")&&!el("edit-load").disabled,"network recovery");
  check(document.documentElement.scrollWidth<=window.innerWidth,"no overflow");out.textContent=`PASS: ${count} browser assertions`;
 }catch(error){out.textContent=`FAIL: ${error.message}`;}
})();
