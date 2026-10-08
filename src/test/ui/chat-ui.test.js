"use strict";
(async () => {
  const output = document.createElement("pre");
  output.id = "test-result";
  document.body.append(output);
  let assertions = 0;
  function check(condition, description) {
    if (!condition) throw new Error(description);
    assertions++;
  }
  const get = selector => document.querySelector(selector);
  const submit = () => get("#chat-form").dispatchEvent(new Event("submit", { cancelable: true }));
  const settle = () => new Promise(resolve => setTimeout(resolve, 10));
  const reply = (statusCode, body) => ({ status: statusCode, ok: statusCode === 200, json: async () => body });
  let requests = [];
  let response = reply(200, { message: "Hello", tools: [] });
  window.fetch = async (url, options) => { requests.push({ url, options }); return response; };
  try {
    check(document.documentElement.scrollWidth <= window.innerWidth, "layout has no horizontal overflow");
    get("#message").value = "   "; submit();
    check(requests.length === 0 && get("#status").textContent.includes("1–2000"), "blank message blocked");
    get("#message").value = "Weather?";
    get("#trip-id").value = "1-1-1-1-1"; submit();
    check(requests.length === 0 && get("#status").textContent.includes("UUID"), "short UUID blocked");
    get("#trip-id").value = "";
    get(".suggestion").click();
    check(get("#message").value.includes("Jaipur") && requests.length === 0, "suggestion only fills input");

    let finish;
    window.fetch = (url, options) => {
      requests.push({ url, options });
      return new Promise(resolve => { finish = resolve; });
    };
    submit(); submit();
    check(requests.length === 1 && get("#send").disabled && get("#clear").disabled, "pending prevents duplicate submits and clear");
    check(!("tripId" in JSON.parse(requests[0].options.body)), "empty trip omitted");
    check(requests[0].url === "/api/v1/ai/chat" && requests[0].options.method === "POST", "same-origin POST");
    finish(reply(200, { message: '<img src=x onerror="window.injected=true">', tools: [] }));
    await settle();
    check(!get("#conversation img") && !window.injected && get("#conversation").textContent.includes("<img"), "AI HTML stays text");
    check(get("#conversation").textContent.includes("No tools used"), "no-tool answer is labelled");
    check(!get("#send").disabled && get("#message").value === "", "controls restored and successful question cleared");

    window.fetch = async (url, options) => { requests.push({ url, options }); return response; };
    get("#message").value = "Forecast?";
    get("#trip-id").value = "e99200d3-6d19-4578-ac4f-20cef94fe41c";
    response = reply(200, { message: "Forecast unavailable", tools: [{ name: "get_trip_weather", result: { isError: true, structuredContent: { code: "UNSUPPORTED" } } }] });
    submit(); await settle();
    check(JSON.parse(requests.at(-1).options.body).tripId === get("#trip-id").value, "trip ID sent when supplied");
    check(get(".evidence.failed pre").textContent.includes("UNSUPPORTED"), "tool error evidence is visible");
    check(get("#status").textContent.includes("tool reported an error"), "tool failure distinguished from HTTP failure");

    get("#trip-id").value = "";
    get("#message").value = "<script>window.injected=true</script>";
    response = reply(200, { message: "Found a destination", tools: [{ name: "search_destinations", result: { isError: false, structuredContent: { name: "<img src=x onerror=window.injected=true>" } } }] });
    submit(); await settle();
    check(!get("#conversation script") && !get("#conversation img") && !window.injected, "user and tool HTML stays text");
    check(get(".evidence:not(.failed) pre").textContent.includes("structuredContent"), "successful tool evidence preserved");

    for (const [code, body, expected] of [[404, {}, "profiles"], [502, { detail: "Assistant unavailable" }, "Assistant unavailable"], [200, null, "unexpected response"]]) {
      response = reply(code, body); get("#message").value = "Keep this question";
      submit(); await settle();
      check(get("#status").textContent.includes(expected) && get("#message").value === "Keep this question" && !get("#send").disabled, `HTTP ${code} error preserves input and restores controls`);
    }
    window.fetch = async () => { throw new TypeError("Failed to fetch"); };
    submit(); await settle();
    check(get("#status").textContent.includes("Could not reach") && !get("#send").disabled, "network failure handled");
    get("#clear").click();
    check(!get(".turn") && get("#conversation").textContent.includes("cleared"), "clear only resets view");
    output.textContent = `PASS: ${assertions} browser assertions`;
  } catch (error) {
    output.textContent = `FAIL: ${error.message}`;
  }
})();
