# Step 30 — Authentic Multi-Source Intelligence Pack

## Current decision: Spark-only

Call Secure Pro currently uses a **Spark-only** production path. Cloud Functions and third-party
provider calls are intentionally not part of the Android runtime. The optional `functions/`
source pack stays in the repository only as a future reference if the project later moves to a
server-backed paid architecture.

`firebase.json` deploys Firestore rules only, and the Android app does not include the Firebase
Functions SDK.

## Active phone intelligence sources

1. Device Contacts — local, highest-confidence user-owned identity source.
2. Call Secure local identity cache — local and offline-first.
3. Firebase `caller_directory` — authenticated Spark-compatible cloud identity directory.
4. Google libphonenumber — offline numbering-plan validation, country/region, formatting and
   line-type metadata.
5. Verified Public & Business Directory bulk import — admin-only import into `caller_directory`.
6. Community reports — hashed, authenticated submissions that can later support reputation
   aggregation without uploading a raw phone number in the report payload.

## Active IP intelligence

1. Local IPv4/IPv6 parser and public/private/reserved classification.
2. Device network transport and local interface address information already shown by the app.

The optional `functions/providers/` pack contains future adapters for RIR RDAP, DNS PTR, IPinfo,
MaxMind, IP2Location, DB-IP, GreyNoise and AbuseIPDB. They are **not active in Spark-only mode**
because secret-bearing provider calls must not be made directly from the Android APK.

## Privacy boundary

- Bulk directory import is for public/business/service numbers only.
- The importer rejects `PERSON` identities so it cannot be used as a private subscriber/KYC
  database loader.
- Third-party provider secrets are never stored in the Android APK or GitHub source.
- IP intelligence must never be presented as a person's exact physical location.
- Community report payloads use SHA-256 phone-number keys and controlled categories.

## Canonical directory key

New directory records use:

`SHA-256(E.164 canonical phone number)`

Example conceptually:

`national input -> E.164 -> SHA-256 -> caller_directory/{hash}`

The Android lookup layer also checks legacy national-format hashes so existing Step 29 test data
continues to resolve while the directory migrates to canonical E.164 keys.

## Spark-only admin import tool

Location:

`tools/directory-import/`

The importer supports CSV validation and controlled Firestore writes. Important safeguards:

- dry-run is the default;
- Google libphonenumber normalization before hashing;
- duplicate canonical-number detection;
- only `BUSINESS` and `UNKNOWN` identity types are accepted;
- source evidence must use an HTTPS URL;
- official source class is required;
- expiry is mandatory or generated with a bounded default;
- commit mode requires an exact project confirmation;
- existing records are protected unless `--overwrite` is explicitly supplied;
- Firebase Admin credentials and input data folders are ignored by Git.

Public client-readable documents are stored in `caller_directory`. Source evidence/audit metadata
is stored separately in `caller_directory_admin`; normal mobile clients cannot read that collection
under the existing deny-by-default Firestore rules.

## CSV columns

`phoneNumber,country,displayName,category,identityType,verificationLevel,source,confidence,sourceClass,sourceUrl,expiresAt`

Allowed source classes:

- `GOVERNMENT_OFFICIAL`
- `REGULATOR_OFFICIAL`
- `ORGANIZATION_OFFICIAL`
- `BUSINESS_OFFICIAL`
- `PUBLIC_SERVICE_OFFICIAL`

## Current next activation sequence

1. Pull and build the Spark-only Android changes.
2. Re-test the existing Firebase caller-directory number to verify legacy hash fallback.
3. Create an admin credential outside the repository when bulk import is actually needed.
4. Run the importer in dry-run mode against a small verified CSV.
5. Import a small batch and verify Number Lookup from both national and `+country-code` formats.
6. Build curated official/public source datasets and import them in controlled batches.
7. Add local caching and community reputation aggregation before considering any paid backend.

## Future optional provider pack

The repository still contains server-side adapters for Twilio Lookup, Telesign Phone ID,
Veriphone, Abstract Phone Validation, RIR RDAP, DNS PTR, IPinfo, MaxMind, IP2Location, DB-IP,
GreyNoise and AbuseIPDB. They are intentionally dormant while the project remains Spark-only.
If a future server-backed plan is adopted, those adapters must remain behind authenticated server
infrastructure; provider credentials must never be moved into the mobile client.
