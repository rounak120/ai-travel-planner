"use strict";
(async()=>{
 const out=document.createElement("pre");out.id="test-result";document.body.append(out);let count=0;const el=id=>document.getElementById(id);
 const check=(ok,text)=>{if(!ok)throw new Error(text);count++;};const settle=()=>new Promise(r=>setTimeout(r,30));
 try{
  // Initial /me check finishes against the isolated fixture server before mocks.
  for(let i=0;i<20&&el("register").disabled;i++)await settle();
  check(document.documentElement.scrollWidth<=window.innerWidth,"account responsive");
  el("username").value="new_user";el("password").value="a-long-test-password";
  let calls=[];window.fetch=async(url,options)=>{calls.push({url,options});return {ok:true,status:201,json:async()=>({username:"new_user"})};};
  el("register").click();await settle();check(calls[0].url.endsWith("/register")&&calls[0].options.method==="POST","register request");
  check(el("password").value===""&&el("account-status").textContent.includes("Account created"),"registration clears password and asks login");
  window.fetch=async()=>({ok:false,status:401,json:async()=>({})});el("password").value="wrong-password";el("account-form").dispatchEvent(new Event("submit",{cancelable:true}));await settle();
  check(el("account-status").textContent.includes("Sign-in failed")&&el("password").value==="","failed login clears password");
  window.fetch=async(url,options)=>{calls.push({url,options});return url.endsWith("/me")?{ok:true,status:200,json:async()=>({username:"new_user"})}:{ok:true,status:204,json:async()=>null};};
  el("password").value="a-long-test-password";el("account-form").dispatchEvent(new Event("submit",{cancelable:true}));await settle();
  check(el("account-form").hidden&&!el("logout").hidden&&el("account-status").textContent.includes("new_user"),"signed-in account state");
  check(calls.find(c=>c.url.endsWith("/login")).options.body instanceof URLSearchParams,"login uses standard form authentication");
  window.fetch=async()=>({ok:false,status:403,json:async()=>({detail:"Session expired"})});el("logout").click();await settle();
  check(el("account-status").textContent.includes("Session expired")&&!el("logout").disabled,"logout error recoverable");
  out.textContent=`PASS: ${count} browser assertions`;
 }catch(error){out.textContent=`FAIL: ${error.message}`;}
})();
