"use strict";
(async () => {
  const output = document.createElement("pre"); output.id = "test-result"; document.body.append(output);
  let assertions = 0;
  function check(condition, text) { if (!condition) throw new Error(text); assertions++; }
  const get = id => document.getElementById(id);
  const submit = () => get("trip-form").dispatchEvent(new Event("submit", { cancelable: true }));
  const settle = () => new Promise(resolve => setTimeout(resolve, 10));
  const id = "e99200d3-6d19-4578-ac4f-20cef94fe41c";
  const fill = () => {
    get("destination").value = " Jaipur "; get("start-date").value = "2099-10-12"; get("end-date").value = "2099-10-13";
    get("budget").value = "5000.25"; get("currency").value = "inr"; get("interests").value = " history, food, ";
  };
  const reply = (status, body) => ({ status, ok: status >= 200 && status < 300, json: async () => body });
  let requests = [];
  let result = reply(201, { id, destination: "<img src=x onerror=window.injected=true>", startDate: "2099-10-12", endDate: "2099-10-13", travelers: 2 });
  let resolve;
  window.fetch = (url, options) => { requests.push({url, options}); return new Promise(done => { resolve = done; }); };
  try {
    check(document.documentElement.scrollWidth <= window.innerWidth, "no horizontal overflow");
    submit(); check(requests.length === 0, "required fields blocked");
    fill(); get("end-date").value = "2099-10-11"; submit();
    check(requests.length === 0 && get("trip-status").textContent.includes("End date"), "reversed dates blocked");
    fill(); get("interests").value = "x".repeat(81); submit();
    check(requests.length === 0 && get("trip-status").textContent.includes("80"), "long interest blocked");
    fill(); submit(); submit();
    check(requests.length === 1 && get("save-trip").disabled, "duplicate pending submission blocked");
    const body = JSON.parse(requests[0].options.body);
    check(requests[0].url === "/api/v1/trips" && requests[0].options.method === "POST", "existing trip API used");
    check(body.currency === "INR" && body.destination === "Jaipur" && body.budget === "5000.25" && body.travelers === 2, "normalized fields and exact decimal budget");
    check(JSON.stringify(body.interests) === '["history","food"]', "comma separated interests normalized");
    resolve(result); await settle();
    check(!get("saved-trip").hidden && get("saved-id").value === id, "ID and success visible");
    check(!get("saved-summary").querySelector("img") && !window.injected, "returned content rendered as text");
    check(get("open-chat").getAttribute("href") === `/?tripId=${id}`, "chat link binds saved ID");
    check(get("open-trip").getAttribute("href") === `/api/v1/trips/${id}`, "retrieval link binds saved ID");
    submit(); check(requests.length === 1, "success locks duplicate creation");
    get("another-trip").click(); check(get("saved-trip").hidden && !get("save-trip").disabled, "explicit new trip resets UI");
    window.fetch = async (url, options) => { requests.push({url, options}); return result; };
    result = reply(400, {detail: "Check trip fields", errors: {currency: "<script>bad</script>"}});
    fill(); submit(); await settle();
    check(get("trip-errors").textContent.includes("currency:") && !get("trip-errors").querySelector("script"), "safe field validation messages");
    check(!get("save-trip").disabled && get("destination").value.trim() === "Jaipur", "validation preserves form");
    result = reply(201, {id: "bad-id"}); submit(); await settle();
    check(get("trip-status").textContent.includes("may have been saved"), "malformed success warns against blind retry");
    window.fetch = async () => { throw new TypeError("network"); }; submit(); await settle();
    check(get("trip-status").textContent.includes("no automatic retry") && !get("save-trip").disabled, "ambiguous network outcome explained");
    output.textContent = `PASS: ${assertions} browser assertions`;
  } catch (error) { output.textContent = `FAIL: ${error.message}`; }
})();
