"use strict";
(async()=>{
  const output=document.createElement("pre");output.id="test-result";document.body.append(output);let count=0;
  const check=(ok,text)=>{if(!ok)throw new Error(text);count++;};const el=id=>document.getElementById(id);const settle=()=>new Promise(resolve=>setTimeout(resolve,10));
  const id="e99200d3-6d19-4578-ac4f-20cef94fe41c";const trip={id,destination:"<img src=x onerror=window.injected=true>",startDate:"2099-10-12",endDate:"2099-10-13",travelers:2,budget:5000,currency:"INR",interests:["food"]};
  let calls=[],response={status:200,ok:true,json:async()=>trip},finish;
  window.fetch=(url,options)=>{calls.push({url,options});return new Promise(resolve=>{finish=resolve;});};
  const submit=()=>el("overview-form").dispatchEvent(new Event("submit",{cancelable:true}));
  try{
    check(calls.length===0,"no automatic requests");
    check(document.querySelectorAll(".app-nav-link").length===6 && document.querySelector('[aria-current="page"]').textContent.includes("Overview"),"complete accessible navigation");
    check(document.documentElement.scrollWidth<=window.innerWidth,"no horizontal overflow");
    check(el("overview-shortcuts").textContent.includes("No shortcuts"),"empty browser-local state");
    el("overview-id").value="bad";submit();check(calls.length===0,"invalid UUID rejected");
    el("overview-id").value=id;submit();submit();check(calls.length===1 && el("overview-load").disabled,"duplicate read prevented");finish(response);await settle();
    check(!el("overview-trip").hidden && el("overview-trip").textContent.includes("5000 INR"),"details rendered");
    check(!el("overview-trip").querySelector("img")&&!window.injected,"trip text safe");
    check(document.querySelector('[data-app-route="/weather.html"]').getAttribute("href").endsWith(id),"trip context propagated to nav");
    check(el("overview-trip").querySelectorAll(".action-link").length===4,"complete trip workflow links");
    check(Wayfarer.shortcuts().length===0,"viewing does not persist browser data");
    el("overview-trip").querySelector(".secondary").click();check(Wayfarer.shortcuts().length===1&&el("overview-shortcuts").querySelectorAll(".shortcut-card").length===1,"explicit shortcut save");
    el("overview-trip").querySelector(".secondary").click();check(Wayfarer.shortcuts().length===1,"shortcut deduplication");
    el("overview-shortcuts").querySelector(".quiet").click();check(Wayfarer.shortcuts().length===0&&calls.length===1,"remove shortcut without server mutation");
    let exported;Wayfarer.download=(data,name)=>{exported={data,name};};el("overview-trip").querySelector(".quiet").click();check(exported.data.id===id&&exported.name.endsWith(".json"),"trip export uses loaded data");
    el("overview-id").dispatchEvent(new Event("input"));check(el("overview-trip").hidden&&!document.querySelector('[data-app-route="/weather.html"]').href.includes("tripId"),"editing ID clears context");
    window.fetch=async()=>({ok:false,status:404,json:async()=>({detail:"Trip not found"})});submit();await settle();check(el("overview-status").textContent.includes("not found")&&el("overview-trip").hidden,"missing trip handled");
    window.fetch=async()=>{throw new TypeError("network");};submit();await settle();check(el("overview-status").textContent.includes("Could not reach")&&!el("overview-load").disabled,"network recovery");
    localStorage.setItem("wayfarer.shortcuts.v1","broken");check(Wayfarer.shortcuts().length===0,"corrupt storage safe");localStorage.removeItem("wayfarer.shortcuts.v1");
    output.textContent=`PASS: ${count} browser assertions`;
  }catch(error){output.textContent=`FAIL: ${error.message}`;}
})();
