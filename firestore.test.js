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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated users are rejected on all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("chats").get());
  await assertFails(unauthDb.collection("calls").get());
  await assertFails(unauthDb.collection("reports").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice",
      online: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Users directory and private PII isolation work cleanly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Alice creates public profile (Android) and Bob creates public profile (iPhone)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_time",
      bio: "On TIME",
      devicePlatform: "ANDROID",
      online: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      handle: "bob_ios",
      devicePlatform: "IPHONE",
      online: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Invalid devicePlatform or ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      devicePlatform: "UNKNOWN_OS",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice creates private PII doc
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      phoneNumber: "+919876543210",
      blockedUids: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can read Alice's public directory profile, CANNOT modify it, CANNOT read Alice's private PII
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
});

test("1-to-1 and group chats enforce membership and filtered queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Alice creates a group chat with Bob
  await assertSucceeds(
    aliceDb.collection("chats").doc("group_1").set({
      name: "Friends Group",
      groupChat: true,
      createdBy: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: ["Alice", "Bob"],
      lastMessagePreview: "Group created",
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Filtered query succeeds for Bob; unfiltered fails; Charlie cannot read
  await assertSucceeds(
    bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(bobDb.collection("chats").get());
  await assertFails(charlieDb.collection("chats").doc("group_1").get());

  // Bob sends a photo/text message in group_1
  await assertSucceeds(
    bobDb.collection("chats").doc("group_1").collection("messages").doc("msg_1").set({
      chatId: "group_1",
      senderId: BOB_UID,
      senderName: "Bob",
      body: "Hello group!",
      mediaType: "TEXT",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 2000),
    })
  );

  // Charlie cannot read or send messages in group_1
  await assertFails(
    charlieDb.collection("chats").doc("group_1").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("chats").doc("group_1").collection("messages").doc("msg_2").set({
      chatId: "group_1",
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      body: "Sneak in",
      mediaType: "TEXT",
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Filtered message query succeeds for Alice
  await assertSucceeds(
    aliceDb
      .collection("chats")
      .doc("group_1")
      .collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
});

test("WebRTC call signaling and ICE candidates enforce participants and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      callType: "VIDEO",
      status: "RINGING",
      offerSdp: "v=0...",
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob answers the call
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "ACTIVE",
      answerSdp: "v=0...",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Bob exchanges ICE candidate
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").collection("candidates").doc("cand_1").set({
      senderId: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:1 1 UDP 2122252543 192.0.2.1 54400 typ host",
      createdAt: new Date(Date.now() - 1500),
    })
  );

  // Charlie cannot read call or candidate
  await assertFails(charlieDb.collection("calls").doc("call_1").get());
  await assertFails(
    charlieDb.collection("calls").doc("call_1").collection("candidates").doc("cand_1").get()
  );

  // Alice ends call -> terminal state lock prevents reopening
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ENDED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Safety reports isolate reporter reads and reject unverified admin email spoof", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: "maminul78338@gmail.com",
      email_verified: false,
    })
    .firestore();
  const realAdminDb = testEnv
    .authenticatedContext("admin_uid", {
      email: "maminul78338@gmail.com",
      email_verified: true,
    })
    .firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      targetUserId: BOB_UID,
      targetName: "Bob",
      category: "Spam",
      details: "Spam links",
      status: "OPEN",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read Alice's report; spoofed admin cannot update
  await assertFails(bobDb.collection("reports").doc("rep_1").get());
  await assertFails(
    spoofedAdminDb.collection("reports").doc("rep_1").update({
      status: "RESOLVED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Verified admin can update status; Alice's filtered query succeeds
  await assertSucceeds(
    realAdminDb.collection("reports").doc("rep_1").update({
      status: "RESOLVED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("reports").where("reporterId", "==", ALICE_UID).get()
  );
});
