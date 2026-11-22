const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const {
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  addDoc,
  collection,
  query,
  where,
  writeBatch,
  serverTimestamp,
} = require("firebase/firestore");

const PROJECT_ID = "tablet-notes-rules-test";
const RULES_PATH = path.resolve(__dirname, "..", "firestore.rules");

let testEnv;

test.before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync(RULES_PATH, "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

test.after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

test.beforeEach(async () => {
  await testEnv.clearFirestore();
});

test("1. User cannot read another user's private blocks or any reports", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "blocks/alice_mallory"), {
      blockerUid: "alice",
      blockedUid: "mallory",
    });
    await setDoc(doc(db, "reports/rep1"), {
      reporterId: "alice",
      targetType: "user",
      targetUserId: "mallory",
      reason: "Spam",
    });
  });

  const bobDb = testEnv.authenticatedContext("bob").firestore();
  const aliceDb = testEnv.authenticatedContext("alice").firestore();

  // Alice can read her own block
  await assertSucceeds(getDoc(doc(aliceDb, "blocks/alice_mallory")));
  // Bob cannot read Alice's block
  await assertFails(getDoc(doc(bobDb, "blocks/alice_mallory")));
  await assertFails(
    getDocs(query(collection(bobDb, "blocks"), where("blockerUid", "==", "alice")))
  );

  // Neither Bob nor even Alice can read reports after creating them
  await assertFails(getDoc(doc(aliceDb, "reports/rep1")));
  await assertFails(getDoc(doc(bobDb, "reports/rep1")));
  // Authenticated user CAN create a valid report
  await assertSucceeds(
    addDoc(collection(bobDb, "reports"), {
      reporterId: "bob",
      targetType: "user",
      targetUserId: "mallory",
      reason: "Inappropriate message",
    })
  );
});

test("2. User cannot list users or usernames collection (only exact get allowed)", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "usernames/alice_n"), { uid: "alice", username: "alice_n" });
    await setDoc(doc(db, "users/alice"), {
      uid: "alice",
      username: "alice_n",
      displayName: "Alice",
      avatarPreset: "cobalt_circle",
    });
  });

  const bobDb = testEnv.authenticatedContext("bob").firestore();
  // Exact single-doc GET succeeds
  await assertSucceeds(getDoc(doc(bobDb, "usernames/alice_n")));
  await assertSucceeds(getDoc(doc(bobDb, "users/alice")));

  // Collection listing MUST fail
  await assertFails(getDocs(collection(bobDb, "users")));
  await assertFails(getDocs(collection(bobDb, "usernames")));

  // Unauthenticated cannot read even single user
  const anonDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anonDb, "users/alice")));
});

test("3. User cannot read a direct_chat or group (or its messages) they are not a member of", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "direct_chats/alice_bob"), {
      chatId: "alice_bob",
      participants: ["alice", "bob"],
      lastMessageText: "Secret",
    });
    await setDoc(doc(db, "direct_chats/alice_bob/messages/m1"), {
      senderId: "alice",
      text: "Hello Bob",
    });
    await setDoc(doc(db, "groups/grp1"), {
      groupId: "grp1",
      name: "Physics",
      ownerId: "alice",
      memberIds: ["alice", "bob"],
      roles: { alice: "owner", bob: "member" },
      whoCanSend: "everyone",
    });
    await setDoc(doc(db, "groups/grp1/messages/gm1"), {
      senderId: "alice",
      text: "Group secret",
    });
  });

  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  const charlieDb = testEnv.authenticatedContext("charlie").firestore();

  await assertSucceeds(getDoc(doc(aliceDb, "direct_chats/alice_bob")));
  await assertSucceeds(getDoc(doc(aliceDb, "direct_chats/alice_bob/messages/m1")));
  await assertSucceeds(getDoc(doc(aliceDb, "groups/grp1")));
  await assertSucceeds(getDoc(doc(aliceDb, "groups/grp1/messages/gm1")));

  // Non-member Charlie is denied everywhere
  await assertFails(getDoc(doc(charlieDb, "direct_chats/alice_bob")));
  await assertFails(getDoc(doc(charlieDb, "direct_chats/alice_bob/messages/m1")));
  await assertFails(getDoc(doc(charlieDb, "groups/grp1")));
  await assertFails(getDoc(doc(charlieDb, "groups/grp1/messages/gm1")));
});

test("4. Non-friend or pending friend cannot create a direct_chat; accepted friends can", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "friendships/alice_charlie"), {
      pairId: "alice_charlie",
      uidA: "alice",
      uidB: "charlie",
      users: ["alice", "charlie"],
      status: "pending",
      requesterId: "alice",
      receiverId: "charlie",
    });
    await setDoc(doc(db, "friendships/alice_bob"), {
      pairId: "alice_bob",
      uidA: "alice",
      uidB: "bob",
      users: ["alice", "bob"],
      status: "accepted",
      requesterId: "alice",
      receiverId: "bob",
    });
  });

  const aliceDb = testEnv.authenticatedContext("alice").firestore();

  // Stranger mallory -> no friendship doc
  await assertFails(
    setDoc(doc(aliceDb, "direct_chats/alice_mallory"), {
      chatId: "alice_mallory",
      participants: ["alice", "mallory"],
    })
  );

  // Pending friendship charlie -> rejected
  await assertFails(
    setDoc(doc(aliceDb, "direct_chats/alice_charlie"), {
      chatId: "alice_charlie",
      participants: ["alice", "charlie"],
    })
  );

  // Accepted friendship bob -> succeeds
  await assertSucceeds(
    setDoc(doc(aliceDb, "direct_chats/alice_bob"), {
      chatId: "alice_bob",
      participants: ["alice", "bob"],
    })
  );
});

test("5. Blocked user cannot send a friend request or send messages in direct_chats", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "blocks/alice_bob"), {
      blockerUid: "alice",
      blockedUid: "bob",
    });
    await setDoc(doc(db, "friendships/alice_bob"), {
      pairId: "alice_bob",
      uidA: "alice",
      uidB: "bob",
      users: ["alice", "bob"],
      status: "accepted",
      requesterId: "alice",
      receiverId: "bob",
    });
    await setDoc(doc(db, "direct_chats/alice_bob"), {
      chatId: "alice_bob",
      participants: ["alice", "bob"],
    });
  });

  const bobDb = testEnv.authenticatedContext("bob").firestore();

  // Blocked user Bob cannot send friend request to Alice or Charlie? (Alice blocked Bob)
  await assertFails(
    setDoc(doc(bobDb, "friendships/alice_bob"), {
      pairId: "alice_bob",
      uidA: "alice",
      uidB: "bob",
      users: ["alice", "bob"],
      status: "pending",
      requesterId: "bob",
      receiverId: "alice",
    })
  );

  // Blocked user Bob cannot write a message in existing chat with Alice
  await assertFails(
    setDoc(doc(bobDb, "direct_chats/alice_bob/messages/m_blocked"), {
      senderId: "bob",
      text: "Hey",
      createdAt: serverTimestamp(),
    })
  );
});

test("6. Group member cannot perform admin/owner actions and cannot send when whoCanSend == admins_only", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "groups/grp_math"), {
      groupId: "grp_math",
      name: "Math",
      ownerId: "alice",
      memberIds: ["alice", "bob", "charlie"],
      roles: { alice: "owner", bob: "admin", charlie: "member" },
      whoCanSend: "admins_only",
    });
  });

  const ownerDb = testEnv.authenticatedContext("alice").firestore();
  const adminDb = testEnv.authenticatedContext("bob").firestore();
  const memberDb = testEnv.authenticatedContext("charlie").firestore();

  // Regular member cannot send message when whoCanSend == admins_only
  await assertFails(
    setDoc(doc(memberDb, "groups/grp_math/messages/m_charlie"), {
      senderId: "charlie",
      senderName: "Charlie",
      text: "I shouldn't be able to send",
      createdAt: serverTimestamp(),
    })
  );

  // Admin CAN send when whoCanSend == admins_only
  await assertSucceeds(
    setDoc(doc(adminDb, "groups/grp_math/messages/m_bob"), {
      senderId: "bob",
      senderName: "Bob",
      text: "Admin announcement",
      createdAt: serverTimestamp(),
    })
  );

  // Regular member cannot change group info, whoCanSend, or delete group
  await assertFails(
    updateDoc(doc(memberDb, "groups/grp_math"), { whoCanSend: "everyone" })
  );
  await assertFails(deleteDoc(doc(memberDb, "groups/grp_math")));

  // Admin cannot delete group or promote self to owner
  await assertFails(deleteDoc(doc(adminDb, "groups/grp_math")));
  await assertFails(
    updateDoc(doc(adminDb, "groups/grp_math"), { "roles.charlie": "admin" })
  );

  // Owner CAN promote charlie to admin
  await assertSucceeds(
    updateDoc(doc(ownerDb, "groups/grp_math"), { "roles.charlie": "admin" })
  );
});

test("7. User cannot overwrite existing username or store email/phone in public users profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "usernames/alice_1"), {
      uid: "alice",
      username: "alice_1",
    });
  });

  const bobDb = testEnv.authenticatedContext("bob").firestore();

  // Overwriting or creating taken username fails
  await assertFails(
    setDoc(doc(bobDb, "usernames/alice_1"), {
      uid: "bob",
      username: "alice_1",
    })
  );
  await assertFails(updateDoc(doc(bobDb, "usernames/alice_1"), { uid: "bob" }));

  // Storing private email inside users/{uid} fails even in atomic batch with valid username
  const badBatch = writeBatch(bobDb);
  badBatch.set(doc(bobDb, "usernames/bob_1"), { uid: "bob", username: "bob_1" });
  badBatch.set(doc(bobDb, "users/bob"), {
    uid: "bob",
    username: "bob_1",
    displayName: "Bob",
    email: "bob@secret.com",
  });
  await assertFails(badBatch.commit());

  // Valid atomic signup batch succeeds without email/phone
  const goodBatch = writeBatch(bobDb);
  goodBatch.set(doc(bobDb, "usernames/bob_1"), { uid: "bob", username: "bob_1" });
  goodBatch.set(doc(bobDb, "users/bob"), {
    uid: "bob",
    username: "bob_1",
    displayName: "Bob",
    avatarPreset: "cobalt_circle",
  });
  await assertSucceeds(goodBatch.commit());
});

test("8. Messages are immutable (no update), max 2000 chars, and cannot delete another user's message", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "direct_chats/alice_bob"), {
      chatId: "alice_bob",
      participants: ["alice", "bob"],
    });
    await setDoc(doc(db, "direct_chats/alice_bob/messages/m_alice"), {
      senderId: "alice",
      text: "Original text",
    });
  });

  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  const bobDb = testEnv.authenticatedContext("bob").firestore();

  // Alice cannot edit her own message
  await assertFails(
    updateDoc(doc(aliceDb, "direct_chats/alice_bob/messages/m_alice"), {
      text: "Edited",
    })
  );

  // Message > 2000 chars fails
  await assertFails(
    setDoc(doc(aliceDb, "direct_chats/alice_bob/messages/m_long"), {
      senderId: "alice",
      text: "x".repeat(2001),
      createdAt: serverTimestamp(),
    })
  );

  // Bob cannot delete Alice's message
  await assertFails(
    deleteDoc(doc(bobDb, "direct_chats/alice_bob/messages/m_alice"))
  );

  // Alice CAN delete her own message
  await assertSucceeds(
    deleteDoc(doc(aliceDb, "direct_chats/alice_bob/messages/m_alice"))
  );
});
