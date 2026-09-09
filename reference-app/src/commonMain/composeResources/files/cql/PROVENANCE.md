# Bundled CQL — provenance

WHO SMART immunizations decision logic (measles MCV1 due), bundled **verbatim**
for the IMMZ.D due-chip. Do not hand-edit — re-extract from the source package.

## Source

- Package: `smart.who.int.immunizations` **0.2.0** (canonical http://smart.who.int/immunizations)
- Local copy: `km-probe/corpus-scan/smart-immunizations/package/` (the corpus accepted against kotlin-fhir rc03)
- CQL extracted from each `Library-*.json` resource's base64 `text/cql` attachment (unmodified).
- FHIRHelpers 4.0.1 is not in the WHO package (standard HL7 artifact); copied from smart-cxca-kmp/cxca-demo.
- `fhir-modelinfo-4.0.1.xml` copied from smart-cxca-kmp/cxca-cql/modelinfo (handed to the evaluator as a String).
- ValueSet expansions bundled: IMMZ.Z.DE9 (measles-containing, 15 codes), IMMZ.Z.LiveAttenuated (42 codes).
- Extracted 2026-09-08 from package 0.2.0.

## Entry point

`IMMZD2DTMeaslesLowTransmissionLogic` → define `"Client is due for MCV1"` (MCV1 at 12 months).

## sha256

```
6748661c16fe66dd07a68f07f77f7fea44e8b933d265dcc3c575964ba8329591  FHIRHelpers.cql
1f744e8536cb24964f7ca0dd621dd61b4f4f9c6d88948f3d15f2cdc2a050e28c  IMMZCommon.cql
a5522b8f571fddd757cf5da234b1fc5b24a2d0fc37ee49d522f908e618e43596  IMMZConcepts.cql
3e2f79323ddd39bc789ac953a02cd0dde954161c4fac4192ddc47a1eadf35403  IMMZD2DTMeaslesElements.cql
42fad389e5d61e2613fdb2db3b14c4a594a16748658fde20ee5a4e1fce11c438  IMMZD2DTMeaslesEncounterElements.cql
a87e2baf338198c5a84f2f34a7f564fe522c6b85e83a22d153d57a3d82aeb80d  IMMZD2DTMeaslesLowTransmissionLogic.cql
b0ffe4dee34839a133d85b54f13b4cd1ad187ac2be0039588afb453335efa09b  IMMZElements.cql
065dd77a7dd682f28a71a69fa68664806feaddbd18fed2a6fe2fb4cb427825cd  IMMZEncounterElements.cql
93b6f38c9ebcc3f0463b5da0c5b5f086c3ac57c15af89380ed7f563e00cacd02  WHOCommon.cql
ec6596fce412d250b226fc1c1c59ceed369df2f25a09b6be5e81ee3de3c6c8f8  WHOConcepts.cql
7f32f84f1ceded8e0c1e9d8d14d20ff8101e566e2312a54298dd680d63f11e07  WHOElements.cql
4a6bde56a03905d0f69ecde60d9eb2685151b5553b65864f69563eb90cac54b0  WHOEncounterElements.cql
0f6eba2f07ec636e2a5cdaac15a6b27911b11d2eb52ba8a10664af4e4f540538  ValueSet-IMMZ.Z.DE9.json
bd88e83eed072ca68f155625a56486dab039a4b46d24b5e249fe247e4debfd73  ValueSet-IMMZ.Z.LiveAttenuated.json
16fa8119e074ebfb6a301b58af72417c2360dca899e89f6e3bd1d9a0e4789722  fhir-modelinfo-4.0.1.xml
```
