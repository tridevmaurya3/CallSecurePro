"use strict";

const { initializeApp } = require("firebase-admin/app");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { runPhoneProviders, normalizePhoneInput } = require("./providers/phoneProviders");
const { runIpProviders, normalizeIp } = require("./providers/ipProviders");

initializeApp();

const REGION = "asia-south1";
const providerConfigSecret = defineSecret("CALLSECURE_PROVIDER_CONFIG");

function requireAuthenticated(request) {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  }
}

function readProviderConfig() {
  const raw = providerConfigSecret.value();
  if (!raw || !raw.trim()) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch (_) {
    throw new HttpsError(
      "failed-precondition",
      "CALLSECURE_PROVIDER_CONFIG is not valid JSON."
    );
  }
}

function successfulEvidence(results) {
  return results.filter((item) => item && item.status === "OK");
}

function configuredSources(results) {
  return results
    .filter((item) => item && item.status !== "NOT_CONFIGURED")
    .map((item) => item.source);
}

const commonOptions = {
  region: REGION,
  timeoutSeconds: 15,
  memory: "256MiB",
  maxInstances: 10,
  enforceAppCheck: false,
  secrets: [providerConfigSecret]
};

exports.lookupPhoneIntelligence = onCall(
  commonOptions,
  async (request) => {
    requireAuthenticated(request);
    let phoneNumber;
    try {
      phoneNumber = normalizePhoneInput(request.data && request.data.phoneNumber);
    } catch (_) {
      throw new HttpsError("invalid-argument", "phoneNumber must be valid E.164 format.");
    }

    const providerConfig = readProviderConfig();
    const results = await runPhoneProviders(phoneNumber, providerConfig);
    return {
      schemaVersion: 1,
      phoneNumber,
      evidence: successfulEvidence(results),
      providerStatus: results,
      configuredSources: configuredSources(results),
      privacy: {
        privateSubscriberIdentityRequested: false,
        privateSubscriberIdentityReturned: false
      }
    };
  }
);

exports.lookupIpIntelligence = onCall(
  commonOptions,
  async (request) => {
    requireAuthenticated(request);
    let ipAddress;
    try {
      ipAddress = normalizeIp(request.data && request.data.ipAddress);
    } catch (_) {
      throw new HttpsError("invalid-argument", "ipAddress must be a valid IPv4 or IPv6 address.");
    }

    const providerConfig = readProviderConfig();
    const results = await runIpProviders(ipAddress, providerConfig);
    return {
      schemaVersion: 1,
      ipAddress,
      evidence: successfulEvidence(results),
      providerStatus: results,
      configuredSources: configuredSources(results),
      locationNotice: "IP location is approximate network intelligence and must not be treated as a person's exact location."
    };
  }
);
