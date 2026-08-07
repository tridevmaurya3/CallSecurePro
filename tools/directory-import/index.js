"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const { parse } = require("csv-parse/sync");
const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { FieldValue, Timestamp, getFirestore } = require("firebase-admin/firestore");
const { parsePhoneNumberFromString } = require("libphonenumber-js/max");

const PUBLIC_COLLECTION = "caller_directory";
const ADMIN_COLLECTION = "caller_directory_admin";
const DEFAULT_COUNTRY = "IN";
const DEFAULT_EXPIRY_DAYS = 365;
const MAX_EXPIRY_DAYS = 730;
const MAX_ROWS_PER_BATCH = 200;

const ALLOWED_IDENTITY_TYPES = new Set(["BUSINESS", "UNKNOWN"]);
const ALLOWED_VERIFICATION_LEVELS = new Set(["VERIFIED", "UNVERIFIED"]);
const ALLOWED_SOURCE_CLASSES = new Set([
  "GOVERNMENT_OFFICIAL",
  "REGULATOR_OFFICIAL",
  "ORGANIZATION_OFFICIAL",
  "BUSINESS_OFFICIAL",
  "PUBLIC_SERVICE_OFFICIAL"
]);

function parseArgs(argv) {
  const args = {
    input: "data/directory.csv",
    project: "",
    confirmProject: "",
    defaultCountry: DEFAULT_COUNTRY,
    expiresDays: DEFAULT_EXPIRY_DAYS,
    commit: false,
    overwrite: false,
    allowPossible: false
  };

  for (let index = 0; index < argv.length; index++) {
    const token = argv[index];
    const next = () => {
      if (index + 1 >= argv.length) {
        throw new Error(`Missing value after ${token}`);
      }
      index += 1;
      return argv[index];
    };

    if (token === "--commit") {
      args.commit = true;
    } else if (token === "--overwrite") {
      args.overwrite = true;
    } else if (token === "--allow-possible") {
      args.allowPossible = true;
    } else if (token === "--input") {
      args.input = next();
    } else if (token.startsWith("--input=")) {
      args.input = token.substring("--input=".length);
    } else if (token === "--project") {
      args.project = next();
    } else if (token.startsWith("--project=")) {
      args.project = token.substring("--project=".length);
    } else if (token === "--confirm-project") {
      args.confirmProject = next();
    } else if (token.startsWith("--confirm-project=")) {
      args.confirmProject = token.substring("--confirm-project=".length);
    } else if (token === "--default-country") {
      args.defaultCountry = next();
    } else if (token.startsWith("--default-country=")) {
      args.defaultCountry = token.substring("--default-country=".length);
    } else if (token === "--expires-days") {
      args.expiresDays = Number(next());
    } else if (token.startsWith("--expires-days=")) {
      args.expiresDays = Number(token.substring("--expires-days=".length));
    } else if (token === "--help" || token === "-h") {
      printHelp();
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${token}`);
    }
  }

  args.defaultCountry = String(args.defaultCountry || DEFAULT_COUNTRY).trim().toUpperCase();
  if (!/^[A-Z]{2}$/.test(args.defaultCountry)) {
    throw new Error("--default-country must be a two-letter ISO country code, for example IN");
  }
  if (!Number.isInteger(args.expiresDays)
      || args.expiresDays < 1
      || args.expiresDays > MAX_EXPIRY_DAYS) {
    throw new Error(`--expires-days must be an integer from 1 to ${MAX_EXPIRY_DAYS}`);
  }

  return args;
}

function printHelp() {
  console.log(`
CallSecurePro Spark-only verified directory importer

Dry run (default):
  node index.js --input=data/directory.csv --default-country=IN

Commit to Firestore:
  node index.js --input=data/directory.csv --project=YOUR_PROJECT_ID \\
    --confirm-project=YOUR_PROJECT_ID --commit

Optional:
  --overwrite        Explicitly replace existing canonical records.
  --allow-possible   Accept possible-but-not-valid libphonenumber results.
  --expires-days=N   Default expiry when CSV expiresAt is blank (1-${MAX_EXPIRY_DAYS}).

Authentication for --commit uses Google Application Default Credentials. Keep service-account
JSON outside the repository and point GOOGLE_APPLICATION_CREDENTIALS to that local file.
`);
}

function sha256(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

function clean(value) {
  return value === undefined || value === null ? "" : String(value).trim();
}

function bounded(value, field, maxLength, required = false) {
  const text = clean(value);
  if (required && !text) {
    throw new Error(`${field} is required`);
  }
  if (text.length > maxLength) {
    throw new Error(`${field} must be <= ${maxLength} characters`);
  }
  return text;
}

function parseHttpsUrl(value) {
  const text = bounded(value, "sourceUrl", 1000, true);
  let url;
  try {
    url = new URL(text);
  } catch (_) {
    throw new Error("sourceUrl must be a valid HTTPS URL");
  }
  if (url.protocol !== "https:") {
    throw new Error("sourceUrl must use HTTPS");
  }
  if (!url.hostname || url.hostname === "localhost") {
    throw new Error("sourceUrl must point to a real public source");
  }
  return url.toString();
}

function parseConfidence(value) {
  const number = Number(clean(value));
  if (!Number.isInteger(number) || number < 0 || number > 100) {
    throw new Error("confidence must be an integer from 0 to 100");
  }
  return number;
}

function parseExpiry(value, defaultDays) {
  const now = new Date();
  let expiry;
  const raw = clean(value);
  if (raw) {
    expiry = new Date(raw);
    if (Number.isNaN(expiry.getTime())) {
      throw new Error("expiresAt must be an ISO date/time or YYYY-MM-DD");
    }
  } else {
    expiry = new Date(now.getTime() + defaultDays * 24 * 60 * 60 * 1000);
  }

  if (expiry.getTime() <= now.getTime()) {
    throw new Error("expiresAt must be in the future");
  }
  const maximum = now.getTime() + MAX_EXPIRY_DAYS * 24 * 60 * 60 * 1000;
  if (expiry.getTime() > maximum) {
    throw new Error(`expiresAt cannot be more than ${MAX_EXPIRY_DAYS} days ahead`);
  }
  return expiry;
}

function normalizePhone(record, args) {
  const input = bounded(record.phoneNumber, "phoneNumber", 64, true);
  const rowCountry = clean(record.country).toUpperCase();
  if (rowCountry && !/^[A-Z]{2}$/.test(rowCountry)) {
    throw new Error("country must be a two-letter ISO country code");
  }

  const country = rowCountry || args.defaultCountry;
  const parsed = parsePhoneNumberFromString(input, country);
  if (!parsed || !parsed.number) {
    throw new Error("phoneNumber could not be parsed by libphonenumber");
  }
  if (!parsed.isPossible()) {
    throw new Error("phoneNumber is not possible for its numbering plan");
  }
  if (!parsed.isValid() && !args.allowPossible) {
    throw new Error("phoneNumber is possible but not valid; use --allow-possible only after source verification");
  }

  return {
    e164: parsed.number,
    country: parsed.country || country,
    valid: parsed.isValid()
  };
}

function normalizeRecord(record, rowNumber, args) {
  const phone = normalizePhone(record, args);
  const displayName = bounded(record.displayName, "displayName", 120, true);
  const category = bounded(record.category, "category", 80, false);
  const source = bounded(record.source, "source", 80, true);
  const sourceUrl = parseHttpsUrl(record.sourceUrl);
  const confidence = parseConfidence(record.confidence);
  const expiresAt = parseExpiry(record.expiresAt, args.expiresDays);

  const identityType = clean(record.identityType).toUpperCase();
  if (!ALLOWED_IDENTITY_TYPES.has(identityType)) {
    throw new Error("identityType must be BUSINESS or UNKNOWN; bulk PERSON identity import is blocked");
  }

  const verificationLevel = clean(record.verificationLevel).toUpperCase();
  if (!ALLOWED_VERIFICATION_LEVELS.has(verificationLevel)) {
    throw new Error("verificationLevel must be VERIFIED or UNVERIFIED");
  }

  const sourceClass = clean(record.sourceClass).toUpperCase();
  if (!ALLOWED_SOURCE_CLASSES.has(sourceClass)) {
    throw new Error(`sourceClass must be one of: ${Array.from(ALLOWED_SOURCE_CLASSES).join(", ")}`);
  }

  const hash = sha256(phone.e164);
  return {
    rowNumber,
    hash,
    e164: phone.e164,
    country: phone.country,
    numberingPlanValid: phone.valid,
    publicDoc: {
      status: "PUBLISHED",
      displayName,
      displayNumber: phone.e164,
      category: category || null,
      identityType,
      verificationLevel,
      source,
      confidence,
      expiresAt
    },
    auditDoc: {
      hashVersion: "E164_SHA256_V1",
      canonicalNumber: phone.e164,
      country: phone.country,
      source,
      sourceClass,
      sourceUrl,
      verificationLevel,
      confidence,
      expiresAt
    }
  };
}

function loadAndValidate(inputPath, args) {
  if (!fs.existsSync(inputPath)) {
    throw new Error(`Input file not found: ${inputPath}`);
  }

  const csv = fs.readFileSync(inputPath, "utf8");
  const records = parse(csv, {
    bom: true,
    columns: true,
    skip_empty_lines: true,
    trim: true
  });

  const valid = [];
  const errors = [];
  const seenHashes = new Map();

  records.forEach((record, index) => {
    const rowNumber = index + 2;
    try {
      const normalized = normalizeRecord(record, rowNumber, args);
      if (seenHashes.has(normalized.hash)) {
        throw new Error(
          `duplicate canonical number; first seen on row ${seenHashes.get(normalized.hash)}`
        );
      }
      seenHashes.set(normalized.hash, rowNumber);
      valid.push(normalized);
    } catch (error) {
      errors.push({
        row: rowNumber,
        message: error && error.message ? String(error.message) : "Validation failed"
      });
    }
  });

  return { valid, errors };
}

function reportPath() {
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  return path.join(__dirname, `import-report-${stamp}.json`);
}

function writeReport(inputPath, args, validation, committed, existingCount = 0) {
  const report = {
    generatedAt: new Date().toISOString(),
    mode: committed ? "COMMIT" : "DRY_RUN",
    input: path.basename(inputPath),
    project: committed ? args.project : null,
    validRows: validation.valid.length,
    invalidRows: validation.errors.length,
    existingRecordsDetected: existingCount,
    errors: validation.errors,
    records: validation.valid.map((item) => ({
      row: item.rowNumber,
      hash: item.hash,
      canonicalNumber: item.e164,
      country: item.country,
      displayName: item.publicDoc.displayName,
      source: item.publicDoc.source,
      verificationLevel: item.publicDoc.verificationLevel,
      expiresAt: item.publicDoc.expiresAt.toISOString()
    }))
  };

  const output = reportPath();
  fs.writeFileSync(output, JSON.stringify(report, null, 2), "utf8");
  return output;
}

function chunks(items, size) {
  const output = [];
  for (let index = 0; index < items.length; index += size) {
    output.push(items.slice(index, index + size));
  }
  return output;
}

async function findExisting(db, records) {
  const found = [];
  for (const group of chunks(records, 100)) {
    const refs = group.map((item) => db.collection(PUBLIC_COLLECTION).doc(item.hash));
    const snapshots = await db.getAll(...refs);
    snapshots.forEach((snapshot) => {
      if (snapshot.exists) {
        found.push(snapshot.id);
      }
    });
  }
  return found;
}

async function commitRecords(db, records) {
  let committed = 0;
  for (const group of chunks(records, MAX_ROWS_PER_BATCH)) {
    const batch = db.batch();
    for (const item of group) {
      const publicRef = db.collection(PUBLIC_COLLECTION).doc(item.hash);
      const adminRef = db.collection(ADMIN_COLLECTION).doc(item.hash);

      batch.set(publicRef, {
        status: item.publicDoc.status,
        displayName: item.publicDoc.displayName,
        displayNumber: item.publicDoc.displayNumber,
        category: item.publicDoc.category,
        identityType: item.publicDoc.identityType,
        verificationLevel: item.publicDoc.verificationLevel,
        source: item.publicDoc.source,
        confidence: item.publicDoc.confidence,
        expiresAt: Timestamp.fromDate(item.publicDoc.expiresAt)
      });

      batch.set(adminRef, {
        hashVersion: item.auditDoc.hashVersion,
        canonicalNumber: item.auditDoc.canonicalNumber,
        country: item.auditDoc.country,
        source: item.auditDoc.source,
        sourceClass: item.auditDoc.sourceClass,
        sourceUrl: item.auditDoc.sourceUrl,
        verificationLevel: item.auditDoc.verificationLevel,
        confidence: item.auditDoc.confidence,
        expiresAt: Timestamp.fromDate(item.auditDoc.expiresAt),
        lastVerifiedAt: FieldValue.serverTimestamp(),
        schemaVersion: 1
      });
    }
    await batch.commit();
    committed += group.length;
    console.log(`Committed ${committed}/${records.length} directory records...`);
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const inputPath = path.resolve(__dirname, args.input);
  const validation = loadAndValidate(inputPath, args);

  console.log(`Input: ${inputPath}`);
  console.log(`Valid rows: ${validation.valid.length}`);
  console.log(`Invalid rows: ${validation.errors.length}`);

  if (validation.errors.length) {
    validation.errors.slice(0, 25).forEach((item) => {
      console.error(`Row ${item.row}: ${item.message}`);
    });
    if (validation.errors.length > 25) {
      console.error(`...and ${validation.errors.length - 25} more validation errors.`);
    }
    const output = writeReport(inputPath, args, validation, false);
    console.error(`Validation report: ${output}`);
    process.exitCode = 2;
    return;
  }

  if (!args.commit) {
    const output = writeReport(inputPath, args, validation, false);
    console.log("DRY RUN only. No Firebase writes were performed.");
    console.log(`Validation report: ${output}`);
    return;
  }

  if (!args.project) {
    throw new Error("--project is required with --commit");
  }
  if (args.confirmProject !== args.project) {
    throw new Error("--confirm-project must exactly match --project before writes are allowed");
  }
  if (!validation.valid.length) {
    throw new Error("No valid rows to import");
  }

  initializeApp({
    credential: applicationDefault(),
    projectId: args.project
  });
  const db = getFirestore();
  const existing = await findExisting(db, validation.valid);

  if (existing.length && !args.overwrite) {
    const output = writeReport(inputPath, args, validation, false, existing.length);
    throw new Error(
      `${existing.length} canonical records already exist. Nothing was written. `
      + `Review ${output} and use --overwrite only when replacement is intentional.`
    );
  }

  await commitRecords(db, validation.valid);
  const output = writeReport(inputPath, args, validation, true, existing.length);
  console.log(`Import complete. Report: ${output}`);
}

main().catch((error) => {
  console.error(error && error.message ? error.message : error);
  process.exitCode = 1;
});
