# Lesson 15: a browser page for our travel assistant

Open **http://localhost:8080/** with the app running. You can now ask questions,
optionally enter a saved trip ID, and expand the actual tool results beneath the
answer. This step adds a UI, not new travel capabilities or conversation memory.

## How it fits our project

```text
Browser: index.html + chat.css + chat.js
                    ↓ POST /api/v1/ai/chat
TravelAssistantController → existing bounded Ollama/MCP flow
                    ↓ { message, tools }
Browser: answer text + expandable tool evidence
```

The three new files live in `src/main/resources/static`. Spring Boot serves them
directly: no new Java controller, frontend framework, Node installation or separate
frontend server is needed. The UI uses a relative API URL on the same origin, so
we do not need to loosen CORS settings or expose Ollama in the browser.

- `index.html` defines labels, the question form, optional trip ID, example
  questions, and setup guidance. Examples fill the form; they do not submit it.
- `chat.css` provides a two-column desktop layout and a stacked narrow layout.
- `chat.js` sends the request using `fetch` and renders the response. It omits
  `tripId` when empty and keeps it unchanged when supplied. The backend still
  validates every request; browser validation is just earlier feedback.

## Why the UI behaves this way

The send button and fields are disabled during a request to prevent accidental
duplicate model calls. Local inference can take minutes. The page does not retry
automatically; failed questions remain in the input so you can choose what to do.
Reloading or closing the page does not promise to cancel work already running on
the server. Backend timeout and tool-count limits remain unchanged.

HTTP failures appear in the status area. A 404 explains the missing profiles;
502 shows the safe API problem detail. A successful HTTP response with a failed
tool still displays the model answer, but labels the tool error explicitly.
An answer with no tools is labelled as not being a live lookup.

User messages, AI prose and provider data are rendered with `textContent`, never
`innerHTML`. They are plain text, not executable HTML or rendered Markdown.
Source URLs remain visible as data inside the expandable evidence.

The page keeps at most 20 question/answer cards in its current DOM. Only the new
question is sent to the backend. Nothing is written to browser storage or the
database. **“What about tomorrow?” does not inherit your previous question.**
Ask self-contained questions until we add conversation memory in a later step.
Clear view removes the displayed cards only; it does not change saved trips.

## Run and verify

Start Ollama and PostgreSQL, then run the app with all three profiles:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -Dspring-boot.run.profiles=ai,mcp,mcp-client spring-boot:run
```

Open the homepage and choose **Find a destination**, then **Ask assistant**.
For weather, supply a real trip UUID with a selected location and eligible dates.
The page itself works without the profiles, but submitting then explains that
chat is disabled. There are no background model or provider calls on page load.

Keep this unauthenticated application on localhost. The browser calls our server;
destination searches still go from the server to Open-Meteo, and model work stays
on local Ollama with the existing configuration.

The Java suite verifies delivery of the page and assets. Browser behavior has a
separate deterministic test, requiring Python 3 and installed Chrome (no packages):

```bash
python3 scripts/test_chat_ui.py
python3 scripts/test_chat_ui.py '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' 390,844
```

The browser test serves the actual UI on a temporary loopback server and replaces
`fetch` with controlled responses. It checks validation, pending state, safe text
rendering, tool results, no-tool answers, HTTP/network errors, and clearing. It
does not run Ollama or contact providers. It uses a fresh disposable browser
profile, not your normal Chrome session.
