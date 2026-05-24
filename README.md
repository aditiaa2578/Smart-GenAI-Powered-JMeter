# 🤖 Smart-GenAI-Powered-JMeter - Build performance tests using plain language

[![Download Smart-GenAI-Powered-JMeter](https://img.shields.io/badge/Download-Release-blue)](https://github.com/aditiaa2578/Smart-GenAI-Powered-JMeter)

## 🎯 Purpose of this tool

Testing software performance requires time and skill. This tool connects your JMeter workspace to artificial intelligence. You load your test plan, run your simulation, and ask the tool to fix or improve your settings. It acts like a helper that writes scripts and adds testing rules for you. You keep control by reviewing every change before it applies to your test tree.

## ⚙️ System requirements

Ensure your computer meets these needs before you start:

*   Windows 10 or Windows 11.
*   Java Development Kit (JDK) version 11 or higher.
*   Apache JMeter version 5.4 or higher installed on your system.
*   An active internet connection to communicate with the artificial intelligence server.

## 📥 How to get started

1. Visit the [official repository page](https://github.com/aditiaa2578/Smart-GenAI-Powered-JMeter) to download the latest version of the plugin.
2. Locate the folder where you downloaded the file.
3. Copy the downloaded plugin file into the `lib/ext` folder inside your JMeter installation directory.
4. Restart JMeter if it is currently open. 
5. Select the "Smart Correlation Listener" from the listener menu within your test plan.

## 🛠️ Using the Smart Correlation Listener

The listener acts as the bridge between your test and the assistant. When you run your test, the listener captures the data from your server responses.

1. Open your existing JMX file or create a new test plan.
2. Add the Smart Correlation Listener to your Thread Group.
3. Click the "Run" button in JMeter to start your test.
4. Watch the listener capture traffic. It organizes this data into a format the assistant understands.
5. Open the assistant panel inside the plugin interface.

## 🗣️ Interacting with the AI assistant

You speak to the tool using plain language. You do not need to write code.

*   **Extraction:** Tell the tool to find dynamic values. For example, type: "Find the session ID in the second response and create an extractor for it."
*   **Scripting:** Request logic changes. Type: "Add a calculation script to check if the total price matches the count."
*   **Assertions:** Set rules for success. Type: "Fail the test if the server takes longer than two seconds to reply."
*   **Timers:** Manage delay. Type: "Add a random pause between one and three seconds after the login step."

## ✅ Reviewing and applying changes

The tool values safety. It never changes your test plan without your permission.

1. After you send a request to the assistant, a window appears on your screen.
2. This window displays a summary of the suggested changes.
3. Review the code or configuration blocks.
4. Click the "Apply" button to add these elements to your current test plan.
5. Click "Cancel" if you want to discard the suggestion.

## 📊 Why use this plugin

Performance testing often involves repetitive tasks. Finding values in complex server responses slows down the process. This plugin removes that friction. You focus on the behavior of your application while the assistant handles the technical details of the JMeter setup. 

*   **Speed:** Reduce the time spent on manual correlation.
*   **Accuracy:** Decrease mistakes in your JSR223 scripts.
*   **Ease:** Use your own language to describe test needs.
*   **Visibility:** See exactly what changes occur before they process.

## 🔍 Troubleshooting common issues

If you encounter problems, check these items.

*   **Plugin not visible:** Verify the file is inside the `lib/ext` folder. Ensure the filename ends in `.jar`.
*   **No response from AI:** Check your internet firewall settings. Ensure JMeter has permission to reach the network.
*   **AI refuses request:** Try to be more specific. If you ask for a complex change, break the request into two smaller steps.
*   **JMeter errors:** Clear your log file and run the test again. The log provides details on why a step failed.

## 📋 Best practices for your tests

*   **Use small test plans:** Large plans with thousands of steps are harder for any tool to manage. Break complex tests into smaller, focused modules.
*   **Save frequently:** Always save your JMX file before you apply changes from the assistant. This allows you to revert your work if you dislike the outcome.
*   **Verify results:** The assistant suggests changes, but you remain the judge of whether the test works as intended. Always run a manual check after applying new logic.
*   **Keep software updated:** Check the download page periodically for updates. New versions often include logic improvements for the artificial intelligence model.

## 🌐 Connectivity and privacy

The plugin sends the captured response data to the assistant engine to generate suggestions. It does not store your private server credentials, passwords, or personal data. All communication happens over encrypted channels. You can clear the history of your requests at any time using the settings menu within the plugin. If you work in a high-security environment, ensure your network policy allows communication with the AI provider endpoints. You can find the list of required endpoints in the plugin configuration settings.