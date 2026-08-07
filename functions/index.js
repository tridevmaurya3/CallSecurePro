"use strict";

const { initializeApp } = require("firebase-admin/app");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { runPhoneProviders, normalizePhoneInput } = require("./providers/phoneProviders");
const { runIpProviders, normalizeIp } = require("./providers/ipProviders");

initializeApp();

const REGION = "asia-south1";

function requireAuthenticated(request) {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
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

exports.lookupPhoneIntelligence = onCall(
  {
    region: REGION,
    timeoutSeconds: 15,
    memory: "256MiB",
    maxInstances: 10,
    enforceAppCheck: false
  },
  async (request) => {
    requireAuthenticated(request);
    let phoneNumber;
    try {
      phoneNumber = normalizePhoneInput(request.data && request.data.phoneNumber);
    } catch (_) {
      throw new HttpsError("invalid-argument", "phoneNumber must be valid E.164 format.");
    }

    const results = await runPhoneProviders(phoneNumber);
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
  {
    region: REGION,
    timeoutSeconds: 15,
    memory: "256MiB",
    maxInstances: 10,
    enforceAppCheck: false
  },
  async (request) => {
    requireAuthenticated(request);
    let ipAddress;
    try {
      ipAddress = normalizeIp(request.data && request.data.ipAddress);
    } catch (_) {
      throw new HttpsError("invalid-argument", "ipAddress must be a valid IPv4 or IPv6 address.");
    }

    const results = await runIpProviders(ipAddress);
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
