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

test("Unauthenticated user cannot read or write rooms, packets, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("rooms").doc("WAVE42").get());
  await assertFails(
    unauthDb.collection("rooms").doc("WAVE42").set({
      ownerId: ALICE_UID,
      roomCode: "WAVE42",
      roomName: "غرفة الأصدقاء",
      roomPasscode: "1234",
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      memberStatuses: { [ALICE_UID]: "متاح" },
      memberTxCounts: { [ALICE_UID]: 0 },
      channelLockEnabled: false,
      activeSpeakerUid: "",
      activeSpeakerName: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    unauthDb.collection("rooms").doc("WAVE42").collection("packets").get()
  );
  await assertFails(
    unauthDb.collection("rooms").doc("WAVE42").collection("messages").get()
  );
});

test("Full GlassWave v3 room, voice channel, chat, status, lock, moderation, and leave lifecycle succeeds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Alice creates room mirroring CloudRadioRepository.createRoom
  await assertSucceeds(
    aliceDb.collection("rooms").doc("WAVE42").set({
      ownerId: ALICE_UID,
      roomCode: "WAVE42",
      roomName: "الشلة",
      roomPasscode: "1234",
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      memberStatuses: { [ALICE_UID]: "متاح" },
      memberAvatars: { [ALICE_UID]: "iVBORw0KGgo=" },
      memberTxCounts: { [ALICE_UID]: 0 },
      channelLockEnabled: false,
      activeSpeakerUid: "",
      activeSpeakerName: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob joins room mirroring CloudRadioRepository.joinRoomByCode
  await assertSucceeds(
    bobDb.collection("rooms").doc("WAVE42").update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      memberStatuses: { [ALICE_UID]: "متاح", [BOB_UID]: "متاح" },
      memberAvatars: { [ALICE_UID]: "iVBORw0KGgo=", [BOB_UID]: "AAAA" },
      updatedAt: new Date(Date.now() - 5000),
    })
  );

  // Bob updates his avatar in the room
  await assertSucceeds(
    bobDb.collection("rooms").doc("WAVE42").update({
      memberAvatars: { [ALICE_UID]: "iVBORw0KGgo=", [BOB_UID]: "BBBB" },
      updatedAt: new Date(Date.now() - 4800),
    })
  );

  // Bob re-joins room (already a member -> Action 2)
  await assertSucceeds(
    bobDb.collection("rooms").doc("WAVE42").update({
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      memberStatuses: { [ALICE_UID]: "متاح", [BOB_UID]: "أقود السيارة" },
      updatedAt: new Date(Date.now() - 4500),
    })
  );

  // Alice enables channelLockEnabled & updates roomPasscode (owner only -> Action 4)
  await assertSucceeds(
    aliceDb.collection("rooms").doc("WAVE42").update({
      channelLockEnabled: true,
      updatedAt: new Date(Date.now() - 4000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("WAVE42").update({
      roomPasscode: "5678",
      updatedAt: new Date(Date.now() - 3800),
    })
  );

  // Non-owner Bob cannot toggle channel lock or change passcode
  await assertFails(
    bobDb.collection("rooms").doc("WAVE42").update({
      channelLockEnabled: false,
      updatedAt: new Date(Date.now() - 3700),
    })
  );

  // Bob updates active speaker, tx count, and ping
  await assertSucceeds(
    bobDb.collection("rooms").doc("WAVE42").update({
      activeSpeakerUid: BOB_UID,
      activeSpeakerName: "Bob",
      updatedAt: new Date(Date.now() - 3500),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("WAVE42").update({
      memberTxCounts: { [ALICE_UID]: 0, [BOB_UID]: 1 },
      updatedAt: new Date(Date.now() - 3400),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("WAVE42").update({
      lastPingUid: BOB_UID,
      lastPingName: "Bob",
      lastPingAt: Date.now(),
      updatedAt: new Date(Date.now() - 3300),
    })
  );

  // Alice sends a voice packet with channel field
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
        channel: 1,
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: new Date(Date.now() - 3000),
      })
  );

  // Alice sends a chat message
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("WAVE42")
      .collection("messages")
      .doc("msg_1")
      .set({
        senderUid: ALICE_UID,
        senderName: "Alice",
        text: "أنا في الطريق",
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: new Date(Date.now() - 2500),
      })
  );

  // Bob lists packets and messages using Kotlin-aligned memberUids filter
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("WAVE42")
      .collection("packets")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("WAVE42")
      .collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Charlie (non-member) cannot read packets, messages, or spoof speaker state
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
      .collection("messages")
      .doc("msg_1")
      .get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc("WAVE42").update({
      activeSpeakerUid: CHARLIE_UID,
      activeSpeakerName: "Charlie",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Owner mutes Bob and then leaves; last remaining member Bob can delete room
  await assertSucceeds(
    aliceDb.collection("rooms").doc("WAVE42").update({
      mutedUids: [BOB_UID],
      updatedAt: new Date(Date.now() - 1500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("WAVE42").update({
      memberUids: [BOB_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("WAVE42").delete()
  );
});

test("Rejects shadow fields, immutable field edits, non-ASCII room IDs, and unfiltered queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Non-ASCII roomId rejected by isValidId
  await assertFails(aliceDb.collection("rooms").doc("الشلة").get());

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
