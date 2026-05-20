# Smart GenAI-Powered JMeter Plugin

> **AI-assisted correlation and test-plan editing — live, inside JMeter.**
> Load any JMX, run the test, and let the plugin's Smart Correlation Listener
> capture every response. Then talk to AI in natural language to add extractors,
> JSR223 scripts, assertions, timers — applied directly to your JMeter tree
> with a confirmation dialog before anything is changed.

Built by **[Sam Richard](https://github.com/Sam-Richard-007)**.

---

## Why this plugin?

Traditional JMeter correlation is slow and error-prone:

- You record traffic → script breaks because of dynamic tokens (CSRF, JWT, session IDs).
- You manually hunt for each one in the response, write a regex, add it as a PostProcessor.
- You repeat this dozens of times per script.

This plugin embeds an AI assistant *inside JMeter itself*, so you stop alt-tabbing
between JMeter, a regex tester, and ChatGPT. You see the live response, select what
you want, and the AI builds the extractor — or proposes a JSR223 PostProcessor for
multi-value extractions — and inserts it into the right sampler in your tree.

---

## Features

### 🤖 AI Chat Sidebar
- Lives as a collapsible left sidebar in the **GenAI Smart Correlation Listener**.
- Natural-language requests: *"Add an assertion containing the booking confirmation text to every book-appointment sampler"*.
- Sees your entire JMX tree (all sampler names) so it can target many samplers at once.
- Sees the live response of the selected sampler.
- Returns structured actions that are previewed before any change to your JMX.

### ⚡ Auto-Correlate
- One-click AI pass over all captured samples.
- Suggests Regex / JSONPath / Boundary extractors per sampler.
- **Detects duplicates** — extractors already present in the tree are flagged and skipped.
- All proposals reviewed in a checkbox table with editable variable names before applying.

### 🔍 Smart Regex Extractor
- Right-click any selected text in the response → opens a Smart Extractor dialog.
- Explicit **Left Boundary**, **Value**, **Right Boundary** fields — not just "the value".
- **🔍 Auto-Detect** finds stable anchors (JSON keys, HTML attrs, headers).
- **🤖 AI Suggest** asks AI for the best boundaries with reasoning.
- **Test** captures a live preview against the current response.
- Applies directly to the matching sampler's children in your loaded JMX.

### 🪄 Multi-Group AI Extractor
- Select a large region (e.g. an HTML table) → click **🪄 Multi-Group (AI)**.
- AI generates a **JSR223 PostProcessor (Groovy)** that captures every value into
  separate JMeter variables in one shot.
- Goes through the same proposal-preview workflow.

### 📎 Send Response to AI
- A button under the response viewer attaches the current response body to the AI chat.
- AI can now read the response and pick actual confirmation text for assertions,
  spot dynamic tokens, etc.

### ⋮ Recorded Reference (baseline)
- Load a `.jmx` (recorded by JMeter's Test Script Recorder) or a `.har` file as baseline.
- AI receives **both** the recorded values and the live response — it can compare and
  identify what changed and needs to be correlated.

### Response Viewer (like View Results Tree)
- Tabbed layout: **Sampler Result / Request / Response Data**.
- **Ctrl+F** in the response body — incremental search, all matches highlighted yellow,
  current match orange, ◀/▶ to step.
- Right-click any selection for extraction options.

### Confirmation Before Every Change
- **Nothing is written to your JMX without your approval.**
- A unified **ProposalPreviewDialog** lists every proposed change with:
  - Checkbox to apply / skip
  - Editable variable name (you can rename right there)
  - Duplicate warning (amber row) for elements already in the tree
  - Per-row details pane showing reasoning and script
  - Select All / Deselect All / Cancel / Apply Selected

### Live Tree Modification
- All AI / manual actions modify the *currently loaded JMX tree* via JMeter's
  `GuiPackage` + `JMeterTreeModel` — no separate file generated.
- Press **Ctrl+S** in JMeter to save the updated test plan.

---

## Supported AI Providers

| Provider | Free Tier | Default Model | Notes |
|----------|-----------|---------------|-------|
| **Groq** | ✅ Yes | `llama-3.3-70b-versatile` | Fastest inference, best free option. Also: `llama-3.1-8b-instant`, `openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `qwen/qwen3-32b`, `groq/compound`, `meta-llama/llama-4-scout-17b-16e-instruct` |
| **Google Gemini** | ✅ Yes | `gemini-2.5-flash` | Also: `gemini-2.5-flash-lite`, `gemini-2.5-pro`, `gemini-flash-latest`, `gemini-pro-latest` |
| **Meta Llama** | Paid | `Llama-4-Scout-17B-16E-Instruct` | Official `api.llama.com` or Together AI |

Configure via **Tools → GenAI Correlation Plugin → AI Settings**.

---

## Installation

### Requirements
- **JMeter 5.6.3** or later
- **Java 17** or later
- (one of) Groq / Gemini / Meta Llama API key

### Steps
1. Download `genai-jmeter-plugin-1.0.0-jmeter.jar` from the
   [Releases page](https://github.com/Sam-Richard-007/Smart-GenAI-Powered-JMeter/releases).
2. Drop it into `<JMETER_HOME>/lib/ext/`.
3. Restart JMeter.
4. Open **Tools → GenAI Correlation Plugin** to configure your AI provider.

> The shipped JAR is a fat / shaded JAR (~4.6 MB) — it bundles OkHttp + Gson + Commons,
> with shaded package relocation to avoid classpath conflicts with JMeter's own deps.

---

## Quick Start

### 1. Configure AI
- `Tools → GenAI Correlation Plugin → ⚙ AI Settings`.
- Pick a provider, paste your API key, select a model, **Test Active Provider**.

### 2. Open your script
- `File → Open` your recorded `.jmx`.

### 3. Add the listener
- Right-click your Thread Group → `Add → Listeners → GenAI Smart Correlation Listener`.

### 4. (Optional) Load a recorded baseline
- Click `⋮ Recorded Ref` in the listener toolbar → `Load Recorded JMX or HAR...`
- Pick a recorded `.jmx` or `.har` file. AI will see both this baseline and live responses.

### 5. Run
- Click Run. Captured rows appear in the table.

### 6. Correlate
**Option A — Auto:**
- Click **⚡ Auto-Correlate (AI)**.
- Review the proposal dialog → uncheck what you don't want → Apply Selected.

**Option B — Manual selection:**
- Click any row → switch to **Response Data** tab.
- Select the dynamic value → right-click → **Extract Selection**.
- Use AI Suggest for boundaries → Apply.

**Option C — AI Chat:**
- Open the left sidebar.
- *"Add a 500ms timer after every checkout sampler."*
- *"Extract the userId from the JSON response of /api/users into a variable."*
- *"Add a Response Assertion for the success text on every booking sampler."*
- Review → Apply.

**Option D — Multi-Group:**
- Select a region (e.g. an HTML table or repeated structure).
- Click **🪄 Multi-Group (AI)** → AI builds a JSR223 PostProcessor.
- Review → Apply.

### 7. Save
- `Ctrl+S` in JMeter. Your script now contains all applied changes.

---

## Building From Source

```bash
git clone https://github.com/Sam-Richard-007/Smart-GenAI-Powered-JMeter
cd Smart-GenAI-Powered-JMeter
mvn package -DskipTests
# Output: target/genai-jmeter-plugin-1.0.0-jmeter.jar (the fat JAR to deploy)
```

### Dependencies
- JMeter 5.6.3 (`provided` scope — supplied by your JMeter install)
- OkHttp 4.12.0 (shaded)
- Gson 2.10.1 (shaded)
- Apache Commons Lang3 / IO (shaded)

---

## Architecture

```
com.genai.jmeter.plugin
├── ai                     ← Provider abstraction (Gemini, Groq, Meta)
│   └── providers
├── core                   ← Menu registration, plugin constants
├── correlate              ← Correlation decision types
├── generator              ← (legacy) JMX generation helpers
├── har                    ← HAR file parser
├── listener               ← Listener-side logic
│   ├── LiveJMXModifier            ← Modifies the live JMeter tree
│   ├── LiveCorrelationEngine      ← Rule-based + AI correlation
│   ├── ChangeProposal             ← Unified "thing to apply" model
│   ├── RecordedReference          ← Baseline loader (.jmx / .har)
│   ├── SmartCorrelationListener   ← The JMeter tree element
│   └── ui
│       ├── SmartListenerPanel     ← Main UI (toolbar + sidebar + content)
│       ├── AIChatPanel            ← Left sidebar, multi-action AI chat
│       ├── ResponseViewerPanel    ← Tabbed View Results Tree-style viewer
│       ├── SamplerListPanel       ← Captured-row table
│       ├── ManualExtractorDialog  ← Smart extractor (L/Value/R + AI)
│       ├── ProposalPreviewDialog  ← Universal apply-before-change UI
│       └── Theme                  ← L&F-resilient coloured buttons
├── scanner                ← Deterministic scan for dynamic values
└── ui                     ← Main plugin window (AI settings + help)
```

The plugin registers via JMeter's standard service-loader mechanism:
- `META-INF/services/org.apache.jmeter.gui.plugin.MenuCreator`
- `META-INF/services/org.apache.jmeter.gui.action.Command`

The Smart Correlation Listener is a standard `AbstractVisualizer`, so it shows up
automatically under `Add → Listeners`.

---

## Roadmap

- [ ] Saved AI presets ("Add OAuth token correlation", "Add CSRF correlation")
- [ ] Diff view between recorded baseline and live response
- [ ] Multi-language script generation (JavaScript via Nashorn, Python via Jython)
- [ ] Export to a self-contained JMX with all baseline data embedded

---

## Limitations & Notes

- Modifies the in-memory JMX tree. **Press Ctrl+S in JMeter to persist.**
- AI calls are billed against your provider account (Groq has a generous free tier).
- The `Tools → GenAI Correlation Plugin` window only opens the AI settings + help —
  *all real work happens inside the Listener*.
- The plugin needs JMeter's GUI to be open. It is not designed for CLI / non-GUI runs.

---

## Contributing

Issues and pull requests welcome at:
**[github.com/Sam-Richard-007/Smart-GenAI-Powered-JMeter](https://github.com/Sam-Richard-007/Smart-GenAI-Powered-JMeter)**

When reporting a bug, please include:
- JMeter version
- Plugin version
- Java version
- AI provider + model
- Steps to reproduce
- Console / `jmeter.log` output if relevant

---

## License

MIT License — see [LICENSE](LICENSE).

---

## Acknowledgements

- Apache JMeter team for the extensible plugin architecture
- Groq, Google, Meta AI for accessible inference APIs
- The JMeter community for years of correlation pain that motivated this
