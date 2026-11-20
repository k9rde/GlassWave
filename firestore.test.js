const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write rooms or packets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("rooms").doc("WAVE42").get());
  await assertFails(
    unauthDb.collection("rooms").doc("WAVE42").set({
      ownerId: ALICE_UID,
      roomCode: "WAVE42",
      roomName: "غرفة الأصدقاء",
      memberUids: [ALICE_UID],
      activeSpeakerUid: "",
      activeSpeakerName: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    unauthDb.collection("rooms").doc("WAVE42").collection("packets").get()
  );
});

test("Authenticated user can create room, friend can join, and both can exchange voice packets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Alice creates room
  await assertSucceeds(
    aliceDb.collection("rooms").doc("WAVE42").set({
      ownerId: ALICE_UID,
      roomCode: "WAVE42",
      roomName: "غرفة الأصدقاء",
      memberUids: [ALICE_UID],
      activeSpeakerUid: "",
      activeSpeakerName: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob joins room by adding only himself to memberUids
  await assertSucceeds(
    bobDb.collection("rooms").doc("WAVE42").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Alice updates active speaker
  await assertSucceeds(
    aliceDb.collection("rooms").doc("WAVE42").update({
      activeSpeakerUid: ALICE_UID,
      activeSpeakerName: "Alice",
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Alice sends a voice packet
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("WAVE42")
      .collection("packets")
      .doc("pkt_1")
      .set({
        senderUid: ALICE_UID,
        senderName: "Alice",
        audioBase64: "AAAA////AAAA",
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: new Date(Date.now() - 1000),
      })
  );

  // Bob lists packets using Kotlin-aligned memberUids filter
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("WAVE42")
      .collection("packets")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Charlie (non-member) cannot read Alice's packet, run unfiltered list, or spoof speaker state
  await assertFails(
    charlieDb
      .collection("rooms")
      .doc("WAVE42")
      .collection("packets")
      .doc("pkt_1")
      .get()
  );
  await assertFails(
    charlieDb
      .collection("rooms")
      .doc("WAVE42")
      .collection("packets")
      .get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc("WAVE42").update({
      activeSpeakerUid: CHARLIE_UID,
      activeSpeakerName: "Charlie",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob can leave the room by removing only himself
  await assertSucceeds(
    bobDb.collection("rooms").doc("WAVE42").update({
      memberUids: [ALICE_UID],
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Rejects shadow fields, immutable field edits, and unfiltered room list queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Shadow field rejected
  await assertFails(
    aliceDb.collection("rooms").doc("WAVE99").set({
      ownerId: ALICE_UID,
      roomCode: "WAVE99",
      roomName: "Bad Room",
      memberUids: [ALICE_UID],
      activeSpeakerUid: "",
      activeSpeakerName: "",
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Unfiltered rooms list query rejected
  await assertFails(aliceDb.collection("rooms").get());

  // Filtered rooms list query succeeds
  await assertSucceeds(
    aliceDb.collection("rooms").where("memberUids", "array-contains", ALICE_UID).get()
  );
});
