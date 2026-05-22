# ScriptGenie AI

> **AI-powered correlation, script-fixing, and test-plan editing — live, inside Apache JMeter.**
> Load any JMX, run the test, and let ScriptGenie AI capture every response. Then talk to AI
> in natural language to add extractors, JSR223/BeanShell scripts, assertions, timers, and more —
> always applied to your loaded JMX with a confirmation dialog before anything changes.

Built by **[Sam Richard](https://github.com/Sam-Richard-007)**.

Repository: **[github.com/Sam-Richard-007/Smart-GenAI-Powered-JMeter](https://github.com/Sam-Richard-007/Smart-GenAI-Powered-JMeter)**

---

## Why ScriptGenie AI?

Traditional JMeter correlation is slow and error-prone:

- Record traffic → script breaks because of dynamic tokens (CSRF, JWT, session IDs).
- You manually hunt for each one in the response, write a regex, add it as a PostProcessor.
- Repeat dozens of times. Then assertions, then JSR223 scripts that don't compile, then jmeter.log debugging.

ScriptGenie AI embeds an AI assistant *inside JMeter itself*. You stop alt-tabbing between JMeter,
a regex tester, and ChatGPT. You see the live response, select what you want, and the AI builds
the extractor — or proposes a JSR223/BeanShell PostProcessor for multi-value extraction — and
inserts it into the right sampler in your tree, after you confirm.

---

## Features at a glance

### 🤖 AI Chat Sidebar (or pop-out window)
- Lives as a collapsible left sidebar in the **ScriptGenie AI Listener**.
- **↗ Pop out** detaches it as its own window with minimize / maximize / close.
- Natural-language requests with **Shift+Enter** for newlines, **Enter** to send.
- Sees your entire JMX tree (all sampler names) so it can target many samplers at once.
- Sees the live response of the selected sampler — plus optional baseline (Recorded Ref).
- Returns structured actions that are previewed before any change to your JMX.

### ⚡ Auto-Correlate (AI) — now with priorities
- One-click AI pass over all captured samples.
- Each suggestion gets a **Priority** label:
  - **ESSENTIAL** — script breaks without this (login token, CSRF, JWT, session, authenticity_token)
  - **IMPORTANT** — likely needed for realistic flows (order_id, transaction_id, appointment_id)
  - **HANDY** — nice to have / reuse (dropdown values, listing IDs, search-result IDs)
- ESSENTIAL/IMPORTANT pre-selected, HANDY left unselected — review and tick what you want.
- **Duplicates** detected against the existing JMX tree and pre-deselected automatically.
- Editable variable names right in the proposal table.

### 🔧 Fix Errors (AI) — Script Fixer
- Scans captured failures: non-2xx HTTP codes, assertion failures, **JSR223/Groovy/BeanShell script errors**.
- Reads the assertion-failure messages from `SampleResult.getAssertionResults()`.
- **Tails the last 120 lines of `jmeter.log`** and sends them to the AI for context.
- Proposes fixes per failure: corrected scripts, missing extractors, missing headers, updated assertions.
- All fixes reviewable in the standard proposal dialog.

### 🔍 Smart Regex Extractor (manual)
- Right-click any selected text in the response → opens a Smart Extractor dialog.
- Explicit **Left Boundary / Value / Right Boundary** fields.
- **🔍 Auto-Detect** finds stable anchors (JSON keys, HTML attrs, headers).
- **🤖 AI Suggest** asks AI for the smartest boundaries with reasoning.
- **Test** captures a live preview against the current response.
- Applies live to the matching sampler's children in your loaded JMX.

### 🪄 Multi-Group Regex (AI)
- Select a representative row (e.g. one row of an HTML table).
- AI generates a **single Regex Extractor** with a multi-group regex + match number = **-1**.
- After running, every match is exposed as `${refName_1_g1}`, `${refName_1_g2}`, …, `${refName_matchNr}`.
- Use directly from a JSR223 PostProcessor or downstream samplers — no separate extractor per value.

### 📎 Send Response to AI / 📦 Attach All
- **+Response** — attach the currently-selected sampler's full response to your next AI message.
- **📦 Attach All** — attach every captured response. Then ask *"add a unique success assertion
  to each sampler based on its actual response text"* and AI emits one tailored assertion per
  sampler (e.g. Login → "Welcome John", Booking → "Appointment booked successfully").
- Per-sampler assertions are always derived from real response content, never the sampler name.

### ⋮ Recorded Reference (baseline)
- Load a `.jmx` (recorded by JMeter's Test Script Recorder) or a `.har` file as a **baseline**.
- AI receives both the recorded values AND the live response — it can identify what changed
  and exactly what needs correlating.

### Response Viewer (like View Results Tree)
- Three tabs: **Sampler Result / Request / Response Data**.
- **Ctrl+F** in the response body — incremental search, all matches yellow, current orange.
- Bottom toolbar (always visible — no clipping): Extract / Multi-Group AI / Send to AI / Find.

### Confirmation Before Every Change
- Nothing is written to your JMX without your approval.
- A unified **Proposal Preview Dialog** lists every proposed change with:
  - Checkbox per row to apply / skip
  - **Priority** column (ESSENTIAL / IMPORTANT / HANDY)
  - Editable variable names
  - **Amber row** = duplicate already in tree (deselected by default)
  - Per-row details pane (reasoning, full script, target sampler)
  - Select All / Deselect All / Cancel / Apply Selected

### BeanShell vs Groovy — respected
- When you ask for "beanshell" anywhere in your request, ScriptGenie AI uses the dedicated
  `BeanShellPreProcessor` / `BeanShellPostProcessor` elements with BeanShell-compatible scripts.
- Default is Groovy via the JSR223 elements.
- Also supports `jython` / `python` and `javascript` / `nashorn` aliases.

### L&F-resilient UI
- Coloured buttons re-apply their colours on every Look-and-Feel switch (Darcula, System, Metal, etc.)
- Sampler table now matches View Results Tree: full names, horizontal scroll, hover tooltips.

---

## Supported AI Providers

| Provider | Free Tier | Default Model | Notes |
|----------|-----------|---------------|-------|
| **Groq** | ✅ | `llama-3.3-70b-versatile` | Fastest free option. Also: `llama-3.1-8b-instant`, `openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `qwen/qwen3-32b`, `groq/compound`, `meta-llama/llama-4-scout-17b-16e-instruct` |
| **Google Gemini** | ✅ | `gemini-2.5-flash` | Also: `gemini-2.5-flash-lite`, `gemini-2.5-pro`, `gemini-flash-latest`, `gemini-pro-latest` |
| **Claude (Anthropic)** | Paid | `claude-sonnet-4-6` | Also: `claude-opus-4-7`, `claude-haiku-4-5-20251001`, `claude-3-5-sonnet-20241022`, `claude-3-5-haiku-20241022` |
| **Meta Llama** | Paid | `Llama-4-Scout-17B-16E-Instruct` | Official `api.llama.com` or Together AI |

Configure via **Tools → ScriptGenie AI - Plugin → ⚙ AI Settings**.

---

## Installation

### Requirements
- **Apache JMeter 5.6.3** or later
- **Java 17** or later
- (one of) Groq / Gemini / Claude / Meta Llama API key

### Steps
1. Download `genai-jmeter-plugin-1.0.0-jmeter.jar` from the
   [Releases page](https://github.com/Sam-Richard-007/Smart-GenAI-Powered-JMeter/releases).
2. Drop it into `<JMETER_HOME>/lib/ext/`.
3. Restart JMeter.
4. Open **Tools → ScriptGenie AI - Plugin** to configure your AI provider.

> The shipped JAR is a fat / shaded JAR (~4.7 MB) — it bundles OkHttp + Gson + Commons,
> with shaded package relocation to avoid classpath conflicts with JMeter's own deps.

---

## Quick Start

### 1. Configure AI
- `Tools → ScriptGenie AI - Plugin → ⚙ AI Settings`.
- Pick a provider, paste your API key, select a model, click **Test Active Provider**.

### 2. Open your script
- `File → Open` your recorded `.jmx`.

### 3. Add the listener
- Right-click your Thread Group → `Add → Listeners → ScriptGenie AI Listener`.

### 4. (Optional) Load a baseline
- Click `⋮ Recorded Ref` in the listener toolbar → `Load Recorded JMX or HAR...`
- Pick a recorded `.jmx` or `.har` file. AI will see both this baseline and live responses.

### 5. Run
- Click Run. Captured rows appear in the table (full sampler names, hover for tooltip).

### 6. Correlate / fix / assert
Pick one (or all) of:

- **⚡ Auto-Correlate** → reviews ESSENTIAL + IMPORTANT items pre-ticked, HANDY left for review.
- **🔧 Fix Errors (AI)** → scans non-2xx + JSR223/BeanShell errors + tails jmeter.log → proposes fixes.
- **AI Chat** → *"Extract the order ID from the response of the Checkout sampler into ${order_id}"*.
- **📦 Attach All + AI Chat** → *"Add a unique success assertion to each sampler based on the actual response text"*.
- **Smart Regex** → select text → right-click → Extract Selection (smart dialog).
- **🪄 Multi-Group (AI)** → select a representative table row → single Regex Extractor with multi-group capture.

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
├── ai                     ← Provider abstraction
│   └── providers
│       ├── GroqProvider         ← Groq (free tier)
│       ├── GeminiProvider       ← Google Gemini (v1beta + systemInstruction)
│       ├── ClaudeProvider       ← Anthropic Claude (v1/messages)
│       └── MetaProvider         ← Meta Llama / Together AI
├── core                   ← Menu registration, plugin constants, branding
├── correlate              ← Correlation decision types
├── generator              ← (legacy) JMX generation helpers
├── har                    ← HAR file parser
├── listener               ← Listener-side logic
│   ├── LiveJMXModifier            ← Modifies the live JMeter tree (regex, JSONPath, BeanShell, etc.)
│   ├── LiveCorrelationEngine      ← Rule-based + AI correlation with priority labels
│   ├── ScriptFixer                ← Failure & JSR223/BeanShell error analyzer
│   ├── ChangeProposal             ← Unified "thing to apply" model with priority
│   ├── RecordedReference          ← Baseline loader (.jmx / .har)
│   ├── SmartCorrelationListener   ← The JMeter tree element ("ScriptGenie AI Listener")
│   └── ui
│       ├── SmartListenerPanel     ← Main UI (toolbar + sidebar + content)
│       ├── AIChatPanel            ← Sidebar / popped-out window AI chat
│       ├── ResponseViewerPanel    ← Tabbed View Results Tree-style viewer
│       ├── SamplerListPanel       ← Captured-row table (resizable, tooltipped, no truncation)
│       ├── ManualExtractorDialog  ← Smart extractor (L/Value/R + AI)
│       ├── ProposalPreviewDialog  ← Universal apply-before-change UI with Priority column
│       └── Theme                  ← L&F-resilient coloured buttons
├── scanner                ← Deterministic scan for dynamic values
└── ui                     ← Main plugin window (AI settings + help)
```

The plugin registers via JMeter's standard service-loader mechanism:
- `META-INF/services/org.apache.jmeter.gui.plugin.MenuCreator`
- `META-INF/services/org.apache.jmeter.gui.action.Command`

The ScriptGenie AI Listener is a standard `AbstractVisualizer`, so it shows up
automatically under `Add → Listeners`.

---

## Limitations & Notes

- Modifies the in-memory JMX tree. **Press Ctrl+S in JMeter to persist.**
- AI calls are billed against your provider account (Groq + Gemini have generous free tiers).
- The `Tools → ScriptGenie AI - Plugin` window only opens AI settings + help —
  *all real work happens inside the Listener*.
- Plugin needs JMeter's GUI to be open — not designed for CLI / non-GUI runs.

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
- Groq, Google, Anthropic, Meta AI for accessible inference APIs
- The JMeter community for years of correlation pain that motivated this project
