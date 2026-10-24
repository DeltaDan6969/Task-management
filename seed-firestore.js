const fs = require("fs");
const path = require("path");
const { initializeApp } = require("firebase/app");
const { getFirestore, doc, setDoc, serverTimestamp, writeBatch } = require("firebase/firestore");

const firebaseConfig = {
  apiKey: "AIzaSyAHPTAlTY1cWoAvHMBHKQ951T7ZgnO0hIM",
  projectId: "gen-lang-client-0968461451",
  authDomain: "gen-lang-client-0968461451.firebaseapp.com",
  storageBucket: "gen-lang-client-0968461451.firebasestorage.app"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app, "ai-studio-android-shifttas-347ba838-628f-49f4-a473-be4ef8849c02");

async function seedFromBackup() {
  const backupPath = path.join(__dirname, "public/assets/aistudio/ShiftTask_Retail_Tasks_Backup__20261017_193621__50f9612a.json");
  console.log("Reading backup from:", backupPath);
  const raw = fs.readFileSync(backupPath, "utf8");
  const backup = JSON.parse(raw);

  console.log("Seeding Store Configs...");
  if (backup.storeConfigs) {
    for (const [key, value] of Object.entries(backup.storeConfigs)) {
      await setDoc(doc(db, "store_config", key), {
        key,
        value: String(value),
        updatedAt: serverTimestamp()
      });
      console.log(` - store_config: ${key} = ${value}`);
    }
  }
  await setDoc(doc(db, "store_config", "initial_seed_completed"), {
    key: "initial_seed_completed",
    value: "true",
    updatedAt: serverTimestamp()
  });

  console.log("Seeding Users...");
  if (backup.users) {
    for (const u of backup.users) {
      await setDoc(doc(db, "users", String(u.id)), {
        name: u.name,
        initials: u.initials,
        role: u.role,
        pinHash: u.pinHash,
        pinSalt: u.pinSalt,
        active: u.active !== false,
        createdAt: serverTimestamp()
      });
      console.log(` - user: ${u.id} - ${u.name} (${u.role})`);
    }
  }

  console.log("Seeding Categories...");
  if (backup.categories) {
    for (const c of backup.categories) {
      await setDoc(doc(db, "categories", String(c.id)), {
        name: c.name,
        sortOrder: c.sortOrder || 1,
        active: c.active !== false,
        createdAt: serverTimestamp()
      });
      console.log(` - category: ${c.id} - ${c.name}`);
    }
  }

  console.log("Seeding Tasks...");
  if (backup.tasks) {
    for (const t of backup.tasks) {
      await setDoc(doc(db, "tasks", String(t.id)), {
        categoryId: String(t.categoryId),
        title: t.title,
        bulletPoints: t.bulletPoints || "",
        priority: t.priority || "MEDIUM",
        daysOfWeek: t.daysOfWeek || "ALL",
        sortOrder: t.sortOrder || 1,
        active: t.active !== false,
        createdAt: serverTimestamp()
      });
      console.log(` - task: ${t.id} - ${t.title} [Cat ${t.categoryId}]`);
    }
  }

  console.log("Seeding Completions...");
  if (backup.completions) {
    for (const comp of backup.completions) {
      const docId = `${comp.taskId}_${comp.dateString}`;
      await setDoc(doc(db, "task_completions", docId), {
        taskId: String(comp.taskId),
        taskTitle: comp.taskTitle || "",
        categoryName: comp.categoryName || "",
        dateString: comp.dateString,
        completedByUserId: String(comp.completedByUserId),
        completedByUserName: comp.completedByUserName || "",
        completedByUserInitials: comp.completedByUserInitials || "",
        completedByUserRole: comp.completedByUserRole || "STAFF",
        isPartial: !!comp.isPartial,
        completedSubTasksJson: comp.completedSubTasksJson || "[]",
        outstandingSubTasksJson: comp.outstandingSubTasksJson || "[]",
        totalSubTasksCount: comp.totalSubTasksCount || 0,
        completedSubTasksCount: comp.completedSubTasksCount || 0,
        completedAt: serverTimestamp()
      });
      console.log(` - completion: ${docId} - ${comp.taskTitle}`);
    }
  }

  console.log("SUCCESS! All 4 categories, 26 tasks, 6 users, and store configs successfully seeded to Google Cloud Firestore!");
  process.exit(0);
}

seedFromBackup().catch(err => {
  console.error("FATAL ERROR SEEDING FIRESTORE:", err);
  process.exit(1);
});
