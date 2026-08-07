"use strict";

const { fetchJson, basicAuth, nonEmpty, compactObject } = require("../lib/http");

function configured(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function normalizePhoneInput(value) {
  const input = String(value || "").trim();
  if (!/^\+[1-9]\d{7,14}$/.test(input)) {
    throw new Error("PHONE_MUST_BE_E164");
  }
  return input;
}

async function twilioLookup(phoneNumber, config = {}) {
  const sid = config.accountSid;
  const token = config.authToken;
  if (!configured(sid) || !configured(token)) {
    return { source: "TWILIO_LOOKUP", status: "NOT_CONFIGURED" };
  }

  const e164 = normalizePhoneInput(phoneNumber);
  const usePaidLineType = config.lineTypeEnabled === true;
  const query = usePaidLineType ? "?Fields=line_type_intelligence" : "";
  const data = await fetchJson(
    `https://lookups.twilio.com/v2/PhoneNumbers/${encodeURIComponent(e164)}${query}`,
    {
      headers: {
        Authorization: basicAuth(sid.trim(), token.trim())
      }
    },
    5000
  );

  const line = data.line_type_intelligence || {};
  return compactObject({
    source: "TWILIO_LOOKUP",
    status: "OK",
    phoneNumber: nonEmpty(data.phone_number),
    nationalFormat: nonEmpty(data.national_format),
    countryCode: nonEmpty(data.country_code),
    callingCountryCode: nonEmpty(data.calling_country_code),
    valid: typeof data.valid === "boolean" ? data.valid : null,
    lineType: nonEmpty(line.type),
    carrierName: nonEmpty(line.carrier_name),
    mobileCountryCode: nonEmpty(line.mobile_country_code),
    mobileNetworkCode: nonEmpty(line.mobile_network_code),
    paidPackageUsed: usePaidLineType
  });
}

async function telesignPhoneId(phoneNumber, config = {}) {
  const customerId = config.customerId;
  const apiKey = config.apiKey;
  if (!configured(customerId) || !configured(apiKey)) {
    return { source: "TELESIGN_PHONE_ID", status: "NOT_CONFIGURED" };
  }

  const e164 = normalizePhoneInput(phoneNumber);
  const digits = e164.substring(1);
  const data = await fetchJson(
    `https://rest-ww.telesign.com/v1/phoneid/${encodeURIComponent(digits)}`,
    {
      method: "POST",
      headers: {
        Authorization: basicAuth(customerId.trim(), apiKey.trim()),
        "Content-Type": "application/json"
      },
      body: "{}"
    },
    6000
  );

  const carrier = data.carrier || {};
  const phoneType = data.phone_type || {};
  const country = data.country || {};
  const location = data.location || {};

  return compactObject({
    source: "TELESIGN_PHONE_ID",
    status: "OK",
    phoneNumber: e164,
    phoneTypeCode: phoneType.code,
    phoneType: nonEmpty(phoneType.description),
    carrierName: nonEmpty(carrier.name),
    countryCode: nonEmpty(country.iso2),
    countryName: nonEmpty(country.name),
    registrationLocation: nonEmpty(location.name),
    referenceId: nonEmpty(data.reference_id)
  });
}

async function veriphoneLookup(phoneNumber, config = {}) {
  const apiKey = config.apiKey;
  if (!configured(apiKey)) {
    return { source: "VERIPHONE", status: "NOT_CONFIGURED" };
  }

  const e164 = normalizePhoneInput(phoneNumber);
  const mode = config.currentCarrierEnabled === true ? "current" : "static";
  const data = await fetchJson(
    `https://api.veriphone.io/v3/verify?phone=${encodeURIComponent(e164)}&mode=${mode}`,
    {
      headers: {
        Authorization: `Bearer ${apiKey.trim()}`
      }
    },
    5000
  );

  return compactObject({
    source: "VERIPHONE",
    status: "OK",
    phoneNumber: nonEmpty(data.e164) || e164,
    valid: typeof data.phone_valid === "boolean" ? data.phone_valid : null,
    lineType: nonEmpty(data.current_line_type) || nonEmpty(data.phone_type),
    carrierName: nonEmpty(data.current_carrier) || nonEmpty(data.carrier),
    originalCarrier: nonEmpty(data.original_carrier),
    ported: typeof data.ported === "boolean" ? data.ported : null,
    countryCode: nonEmpty(data.country_code),
    countryName: nonEmpty(data.country),
    phoneRegion: nonEmpty(data.phone_region),
    nationalFormat: nonEmpty(data.local_number),
    internationalFormat: nonEmpty(data.international_number),
    mode: nonEmpty(data.mode)
  });
}

async function abstractPhoneLookup(phoneNumber, config = {}) {
  const apiKey = config.apiKey;
  if (!configured(apiKey)) {
    return { source: "ABSTRACT_PHONE", status: "NOT_CONFIGURED" };
  }

  const e164 = normalizePhoneInput(phoneNumber);
  const data = await fetchJson(
    `https://phonevalidation.abstractapi.com/v1/?api_key=${encodeURIComponent(apiKey.trim())}&phone=${encodeURIComponent(e164)}`,
    {},
    5000
  );

  const format = data.format || {};
  const country = data.country || {};
  return compactObject({
    source: "ABSTRACT_PHONE",
    status: "OK",
    phoneNumber: e164,
    valid: typeof data.valid === "boolean" ? data.valid : null,
    lineType: nonEmpty(data.line_type),
    carrierName: nonEmpty(data.carrier),
    countryCode: nonEmpty(country.code) || nonEmpty(data.country_code),
    countryName: nonEmpty(country.name) || nonEmpty(data.country_name),
    registeredLocation: nonEmpty(data.location) || nonEmpty(data.registered_location),
    nationalFormat: nonEmpty(format.national) || nonEmpty(data.local_format),
    internationalFormat: nonEmpty(data.international_format),
    riskScore: typeof data.risk_score === "number" ? data.risk_score : null
  });
}

async function runPhoneProviders(phoneNumber, providerConfig = {}) {
  const jobs = [
    ["TWILIO_LOOKUP", () => twilioLookup(phoneNumber, providerConfig.twilio || {})],
    ["TELESIGN_PHONE_ID", () => telesignPhoneId(phoneNumber, providerConfig.telesign || {})],
    ["VERIPHONE", () => veriphoneLookup(phoneNumber, providerConfig.veriphone || {})],
    ["ABSTRACT_PHONE", () => abstractPhoneLookup(phoneNumber, providerConfig.abstractPhone || {})]
  ];

  const results = await Promise.all(jobs.map(async ([source, job]) => {
    try {
      return await job();
    } catch (error) {
      return {
        source,
        status: "ERROR",
        errorCode: error && error.message ? String(error.message) : "PROVIDER_ERROR"
      };
    }
  }));

  return results;
}

module.exports = { runPhoneProviders, normalizePhoneInput };
