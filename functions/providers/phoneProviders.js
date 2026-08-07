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

async function twilioLookup(phoneNumber) {
  const sid = process.env.TWILIO_ACCOUNT_SID;
  const token = process.env.TWILIO_AUTH_TOKEN;
  if (!configured(sid) || !configured(token)) {
    return { source: "TWILIO_LOOKUP", status: "NOT_CONFIGURED" };
  }

  const e164 = normalizePhoneInput(phoneNumber);
  const usePaidLineType = String(process.env.TWILIO_LINE_TYPE_ENABLED || "false")
    .toLowerCase() === "true";
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

async function telesignPhoneId(phoneNumber) {
  const customerId = process.env.TELESIGN_CUSTOMER_ID;
  const apiKey = process.env.TELESIGN_API_KEY;
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

async function runPhoneProviders(phoneNumber) {
  const jobs = [
    ["TWILIO_LOOKUP", () => twilioLookup(phoneNumber)],
    ["TELESIGN_PHONE_ID", () => telesignPhoneId(phoneNumber)]
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
