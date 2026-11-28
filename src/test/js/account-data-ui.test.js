const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const path = require("node:path");
const { test } = require("node:test");

test("account-aware inline application script parses successfully", () => {
  const htmlPath = path.resolve(__dirname, "../../main/resources/static/index.html");
  const html = readFileSync(htmlPath, "utf8");
  const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/gi)];
  const inlineScript = scripts.map((match) => match[1]).find((script) => script.trim().length > 0);

  assert.ok(inlineScript, "expected the application to have an inline script");
  assert.doesNotThrow(() => new Function(inlineScript));
});