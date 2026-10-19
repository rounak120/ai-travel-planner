"use strict";
(() => {
  const el=id=>document.getElementById(id);let busy=false;
  const status=text=>{el("account-status").textContent=text;};
  function changed(){try{localStorage.removeItem("wayfarer.shortcuts.v1");localStorage.setItem("wayfarer.account.changed",String(Date.now()));}catch{/* Storage is optional; authentication is not. */}}
  async function run(action) {
    if(busy)return;busy=true;document.querySelectorAll("button").forEach(b=>b.disabled=true);
    try {await action();} catch(error){status(error.message);} finally {busy=false;document.querySelectorAll("button").forEach(b=>b.disabled=false);}
  }
  async function identity() {
    const response=await fetch("/api/v1/auth/me",{cache:"no-store"});
    if(response.status===401){status("Sign in or create an account.");return;}
    if(!response.ok)throw new Error("Unable to check your account.");
    const data=await response.json();status(`Signed in as ${data.username}.`);el("account-form").hidden=true;el("logout").hidden=false;
  }
  el("account-form").addEventListener("submit",event=>{event.preventDefault();run(async()=>{
    const response=await fetch("/api/v1/auth/login",{method:"POST",body:new URLSearchParams({username:el("username").value,password:el("password").value})});
    el("password").value="";
    if(!response.ok)throw new Error("Sign-in failed. Check your credentials, or reload if your session expired.");
    changed();await identity();
  });});
  el("register").addEventListener("click",()=>{if(!el("account-form").reportValidity())return;run(async()=>{
    await Wayfarer.request("/api/v1/auth/register",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({username:el("username").value,password:el("password").value})});
    el("password").value="";status("Account created. Enter your password again to sign in.");
  });});
  el("logout").addEventListener("click",()=>run(async()=>{
    await Wayfarer.request("/api/v1/auth/logout",{method:"POST"});changed();location.href="/account.html";
  }));
  run(identity);
})();
