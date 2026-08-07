"use strict";

async function fetchJson(url, options = {}, timeoutMs = 4500) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        Accept: "application/json",
        "User-Agent": "CallSecurePro/1.0",
        ...(options.headers || {})
      }
    });

    const text = await response.text();
    let body = null;
    if (text) {
      try {
        body = JSON.parse(text);
      } catch (_) {
        body = { raw: text.slice(0, 1000) };
      }
    }

    if (!response.ok) {
      const error = new Error(`HTTP_${response.status}`);
      error.status = response.status;
      error.body = body;
      throw error;
    }
    return body || {};
  } finally {
    clearTimeout(timeout);
  }
}

function basicAuth(username, password) {
  return "Basic " + Buffer.from(`${username}:${password}`, "utf8").toString("base64");
}

function nonEmpty(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function compactObject(value) {
  if (Array.isArray(value)) {
    return value.map(compactObject).filter((item) => item !== null && item !== undefined);
  }
  if (value && typeof value === "object") {
    const output = {};
    for (const [key, item] of Object.entries(value)) {
      const compacted = compactObject(item);
      if (compacted !== null && compacted !== undefined && compacted !== "") {
        output[key] = compacted;
      }
    }
    return output;
  }
  return value === "" ? null : value;
}

module.exports = { fetchJson, basicAuth, nonEmpty, compactObject };
