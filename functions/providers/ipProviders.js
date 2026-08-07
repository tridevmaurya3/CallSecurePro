"use strict";

const dns = require("node:dns").promises;
const net = require("node:net");
const { fetchJson, basicAuth, nonEmpty, compactObject } = require("../lib/http");

function configured(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function normalizeIp(value) {
  const ip = String(value || "").trim();
  if (!net.isIP(ip)) {
    throw new Error("INVALID_IP");
  }
  return ip;
}

async function reverseDns(ipAddress) {
  const ip = normalizeIp(ipAddress);
  try {
    const names = await Promise.race([
      dns.reverse(ip),
      new Promise((_, reject) => setTimeout(() => reject(new Error("DNS_TIMEOUT")), 2500))
    ]);
    return compactObject({
      source: "DNS_PTR",
      status: "OK",
      hostnames: Array.isArray(names) ? names.slice(0, 5) : []
    });
  } catch (error) {
    return {
      source: "DNS_PTR",
      status: "NO_RESULT",
      errorCode: error && error.code ? String(error.code) : "NO_PTR"
    };
  }
}

async function ipinfoLookup(ipAddress) {
  const token = process.env.IPINFO_TOKEN;
  if (!configured(token)) {
    return { source: "IPINFO", status: "NOT_CONFIGURED" };
  }
  const ip = normalizeIp(ipAddress);
  const data = await fetchJson(
    `https://ipinfo.io/${encodeURIComponent(ip)}/json?token=${encodeURIComponent(token.trim())}`,
    {},
    4500
  );
  return compactObject({
    source: "IPINFO",
    status: "OK",
    ip: nonEmpty(data.ip),
    city: nonEmpty(data.city),
    region: nonEmpty(data.region),
    countryCode: nonEmpty(data.country),
    postalCode: nonEmpty(data.postal),
    timezone: nonEmpty(data.timezone),
    organization: nonEmpty(data.org),
    hostname: nonEmpty(data.hostname)
  });
}

async function maxMindLookup(ipAddress) {
  const accountId = process.env.MAXMIND_ACCOUNT_ID;
  const licenseKey = process.env.MAXMIND_LICENSE_KEY;
  if (!configured(accountId) || !configured(licenseKey)) {
    return { source: "MAXMIND_GEOIP", status: "NOT_CONFIGURED" };
  }
  const ip = normalizeIp(ipAddress);
  const data = await fetchJson(
    `https://geoip.maxmind.com/geoip/v2.1/city/${encodeURIComponent(ip)}`,
    {
      headers: {
        Authorization: basicAuth(accountId.trim(), licenseKey.trim())
      }
    },
    5000
  );
  const country = data.country || {};
  const city = data.city || {};
  const traits = data.traits || {};
  const subdivisions = Array.isArray(data.subdivisions) ? data.subdivisions : [];
  const subdivision = subdivisions.length ? subdivisions[0] : {};
  return compactObject({
    source: "MAXMIND_GEOIP",
    status: "OK",
    countryCode: nonEmpty(country.iso_code),
    countryName: country.names ? nonEmpty(country.names.en) : null,
    region: subdivision.names ? nonEmpty(subdivision.names.en) : null,
    city: city.names ? nonEmpty(city.names.en) : null,
    autonomousSystemNumber: traits.autonomous_system_number,
    autonomousSystemOrganization: nonEmpty(traits.autonomous_system_organization),
    isp: nonEmpty(traits.isp),
    organization: nonEmpty(traits.organization),
    connectionType: nonEmpty(traits.connection_type),
    anonymousProxy: typeof traits.is_anonymous_proxy === "boolean" ? traits.is_anonymous_proxy : null,
    satelliteProvider: typeof traits.is_satellite_provider === "boolean" ? traits.is_satellite_provider : null
  });
}

async function ip2LocationLookup(ipAddress) {
  const key = process.env.IP2LOCATION_API_KEY;
  if (!configured(key)) {
    return { source: "IP2LOCATION_IO", status: "NOT_CONFIGURED" };
  }
  const ip = normalizeIp(ipAddress);
  const data = await fetchJson(
    `https://api.ip2location.io/?key=${encodeURIComponent(key.trim())}&ip=${encodeURIComponent(ip)}&format=json`,
    {},
    4500
  );
  return compactObject({
    source: "IP2LOCATION_IO",
    status: "OK",
    countryCode: nonEmpty(data.country_code),
    countryName: nonEmpty(data.country_name),
    region: nonEmpty(data.region_name),
    city: nonEmpty(data.city_name),
    timezone: nonEmpty(data.time_zone),
    asn: nonEmpty(String(data.asn || "")),
    autonomousSystem: nonEmpty(data.as),
    proxy: typeof data.is_proxy === "boolean" ? data.is_proxy : null
  });
}

async function dbIpLookup(ipAddress) {
  const key = process.env.DBIP_API_KEY;
  if (!configured(key)) {
    return { source: "DB_IP", status: "NOT_CONFIGURED" };
  }
  const ip = normalizeIp(ipAddress);
  const data = await fetchJson(
    `https://api.db-ip.com/v2/${encodeURIComponent(key.trim())}/${encodeURIComponent(ip)}`,
    {},
    4500
  );
  return compactObject({
    source: "DB_IP",
    status: "OK",
    ip: nonEmpty(data.ipAddress),
    continentCode: nonEmpty(data.continentCode),
    countryCode: nonEmpty(data.countryCode),
    countryName: nonEmpty(data.countryName),
    region: nonEmpty(data.stateProv),
    city: nonEmpty(data.city),
    timezone: nonEmpty(data.timeZone),
    isp: nonEmpty(data.isp),
    organization: nonEmpty(data.organization),
    connectionType: nonEmpty(data.connectionType),
    asn: data.asNumber,
    proxy: typeof data.isProxy === "boolean" ? data.isProxy : null,
    threatLevel: nonEmpty(data.threatLevel)
  });
}

async function greyNoiseLookup(ipAddress) {
  const ip = normalizeIp(ipAddress);
  const key = process.env.GREYNOISE_API_KEY;
  if (configured(key)) {
    const data = await fetchJson(
      `https://api.greynoise.io/v3/ip/${encodeURIComponent(ip)}?quick=true`,
      { headers: { key: key.trim() } },
      4500
    );
    return compactObject({
      source: "GREYNOISE",
      status: "OK",
      ip,
      classification: nonEmpty(data.classification),
      trustLevel: nonEmpty(data.trust_level),
      noise: typeof data.noise === "boolean" ? data.noise : null,
      riot: typeof data.riot === "boolean" ? data.riot : null,
      name: nonEmpty(data.name),
      lastSeen: nonEmpty(data.last_seen)
    });
  }

  try {
    const data = await fetchJson(
      `https://api.greynoise.io/v3/community/${encodeURIComponent(ip)}`,
      {},
      3500
    );
    return compactObject({
      source: "GREYNOISE_COMMUNITY",
      status: "OK",
      ip,
      noise: typeof data.noise === "boolean" ? data.noise : null,
      riot: typeof data.riot === "boolean" ? data.riot : null,
      classification: nonEmpty(data.classification),
      name: nonEmpty(data.name),
      lastSeen: nonEmpty(data.last_seen)
    });
  } catch (error) {
    if (error && error.status === 404) {
      return { source: "GREYNOISE_COMMUNITY", status: "NO_RESULT" };
    }
    throw error;
  }
}

async function abuseIpDbLookup(ipAddress) {
  const key = process.env.ABUSEIPDB_API_KEY;
  if (!configured(key)) {
    return { source: "ABUSEIPDB", status: "NOT_CONFIGURED" };
  }
  const ip = normalizeIp(ipAddress);
  const data = await fetchJson(
    `https://api.abuseipdb.com/api/v2/check?ipAddress=${encodeURIComponent(ip)}&maxAgeInDays=90&verbose=false`,
    {
      headers: {
        Key: key.trim(),
        Accept: "application/json"
      }
    },
    4500
  );
  const item = data.data || {};
  return compactObject({
    source: "ABUSEIPDB",
    status: "OK",
    ip: nonEmpty(item.ipAddress),
    public: typeof item.isPublic === "boolean" ? item.isPublic : null,
    ipVersion: item.ipVersion,
    abuseConfidenceScore: item.abuseConfidenceScore,
    countryCode: nonEmpty(item.countryCode),
    usageType: nonEmpty(item.usageType),
    isp: nonEmpty(item.isp),
    domain: nonEmpty(item.domain),
    totalReports: item.totalReports,
    lastReportedAt: nonEmpty(item.lastReportedAt)
  });
}

async function runIpProviders(ipAddress) {
  const jobs = [
    ["DNS_PTR", () => reverseDns(ipAddress)],
    ["IPINFO", () => ipinfoLookup(ipAddress)],
    ["MAXMIND_GEOIP", () => maxMindLookup(ipAddress)],
    ["IP2LOCATION_IO", () => ip2LocationLookup(ipAddress)],
    ["DB_IP", () => dbIpLookup(ipAddress)],
    ["GREYNOISE", () => greyNoiseLookup(ipAddress)],
    ["ABUSEIPDB", () => abuseIpDbLookup(ipAddress)]
  ];

  return Promise.all(jobs.map(async ([source, job]) => {
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
}

module.exports = { runIpProviders, normalizeIp };
