import Foundation
import SwiftUI
import Combine
import CryptoKit
import FirebaseCore
import FirebaseFirestore
import FirebaseAuth

@MainActor
public class FirestoreService: ObservableObject {
    public static let shared = FirestoreService()
    
    // Database Reference to named database matching Android & Web
    private let databaseId = "ai-studio-android-shifttas-347ba838-628f-49f4-a473-be4ef8849c02"
    private var db: Firestore {
        return Firestore.firestore(database: databaseId)
    }
    
    // Published State
    @Published public var isConnected: Bool = true
    @Published public var currentUser: RetailUser? = nil
    @Published public var storeConfig: StoreConfig = StoreConfig()
    @Published public var categories: [RetailCategory] = []
    @Published public var tasks: [RetailTask] = []
    @Published public var users: [RetailUser] = []
    @Published public var completions: [String: TaskCompletion] = [:] // key: taskId_dateString
    @Published public var allCompletionsList: [TaskCompletion] = []
    @Published public var taskNotes: [TaskNote] = []
    @Published public var taskPhotos: [TaskPhoto] = []
    
    // UI Helpers
    @Published public var isDarkMode: Bool = false
    @Published public var selectedDate: Date = Date()
    @Published public var toastMessage: String? = nil
    
    private var listeners: [ListenerRegistration] = []
    private var inactivityTimer: Timer? = nil
    
    public init() {
        // Load local night shift preference
        self.isDarkMode = UserDefaults.standard.bool(forKey: "shifttask_theme_mode_dark")
        startListeners()
    }
    
    deinit {
        stopListeners()
    }
    
    public var selectedDateString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: selectedDate)
    }
    
    public func toggleTheme() {
        isDarkMode.toggle()
        UserDefaults.standard.set(isDarkMode, forKey: "shifttask_theme_mode_dark")
        showToast(isDarkMode ? "Night-Shift Dark Theme activated" : "Light Theme activated")
    }
    
    public func showToast(_ msg: String) {
        toastMessage = msg
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            if self.toastMessage == msg {
                self.toastMessage = nil
            }
        }
    }
    
    // MARK: - Auto-Timeout
    public func touchActivity() {
        inactivityTimer?.invalidate()
        if currentUser != nil {
            inactivityTimer = Timer.scheduledTimer(withTimeInterval: 300, repeats: false) { [weak self] _ in
                Task { @MainActor in
                    self?.signOut()
                    self?.showToast("Signed out due to 5 minutes of inactivity")
                }
            }
        }
    }
    
    public func signOut() {
        currentUser = nil
        inactivityTimer?.invalidate()
    }
    
    // MARK: - Real-time Listeners
    public func startListeners() {
        stopListeners()
        
        // 1. Store Config
        let configListener = db.collection("store_config").addSnapshotListener { [weak self] snapshot, error in
            guard let self = self, let docs = snapshot?.documents else { return }
            var name = self.storeConfig.storeName
            var tagline = self.storeConfig.tagline
            for doc in docs {
                let data = doc.data()
                if let key = data["key"] as? String, let val = data["value"] as? String {
                    if key == "store_name" { name = val }
                    if key == "tagline" { tagline = val }
                }
            }
            self.storeConfig = StoreConfig(storeName: name, tagline: tagline)
        }
        listeners.append(configListener)
        
        // 2. Categories
        let categoriesListener = db.collection("categories").addSnapshotListener { [weak self] snapshot, error in
            guard let self = self, let docs = snapshot?.documents else { return }
            var list: [RetailCategory] = []
            for doc in docs {
                let d = doc.data()
                let name = d["name"] as? String ?? ""
                let sortOrder = d["sortOrder"] as? Int ?? 1
                let active = d["active"] as? Bool ?? true
                if active && !name.isEmpty {
                    list.append(RetailCategory(id: doc.documentID, name: name, sortOrder: sortOrder, active: active))
                }
            }
            self.categories = list.sorted { $0.sortOrder < $1.sortOrder }
        }
        listeners.append(categoriesListener)
        
        // 3. Tasks
        let tasksListener = db.collection("tasks").addSnapshotListener { [weak self] snapshot, error in
            guard let self = self, let docs = snapshot?.documents else { return }
            var list: [RetailTask] = []
            for doc in docs {
                let d = doc.data()
                let catId = String(describing: d["categoryId"] ?? "")
                let title = d["title"] as? String ?? ""
                let bullets = d["bulletPoints"] as? String ?? ""
                let priority = d["priority"] as? String ?? "MEDIUM"
                let days = d["daysOfWeek"] as? String ?? "ALL"
                let sortOrder = d["sortOrder"] as? Int ?? 1
                let scheduleType = d["scheduleType"] as? String
                let oneTimeDate = d["oneTimeDate"] as? String
                let active = d["active"] as? Bool ?? true
                
                if active && !title.isEmpty {
                    list.append(RetailTask(
                        id: doc.documentID,
                        categoryId: catId,
                        title: title,
                        bulletPoints: bullets,
                        priority: priority,
                        daysOfWeek: days,
                        sortOrder: sortOrder,
                        scheduleType: scheduleType,
                        oneTimeDate: oneTimeDate,
                        active: active
                    ))
                }
            }
            self.tasks = list.sorted { $0.sortOrder < $1.sortOrder }
        }
        listeners.append(tasksListener)
        
        // 4. Users
        let usersListener = db.collection("users").addSnapshotListener { [weak self] snapshot, error in
            guard let self = self, let docs = snapshot?.documents else { return }
            var list: [RetailUser] = []
            for doc in docs {
                let d = doc.data()
                let name = d["name"] as? String ?? ""
                let initials = d["initials"] as? String ?? ""
                let role = d["role"] as? String ?? "STAFF"
                let pinHash = d["pinHash"] as? String ?? ""
                let pinSalt = d["pinSalt"] as? String ?? ""
                let active = d["active"] as? Bool ?? true
                
                if active && !name.isEmpty {
                    list.append(RetailUser(
                        id: doc.documentID,
                        name: name,
                        initials: initials,
                        role: role,
                        pinHash: pinHash,
                        pinSalt: pinSalt,
                        active: active
                    ))
                }
            }
            self.users = list.sorted { $0.name < $1.name }
        }
        listeners.append(usersListener)
        
        // 5. Completions
        let completionsListener = db.collection("completions").addSnapshotListener { [weak self] snapshot, error in
            guard let self = self, let docs = snapshot?.documents else { return }
            var map: [String: TaskCompletion] = [:]
            var allList: [TaskCompletion] = []
            for doc in docs {
                let d = doc.data()
                let taskId = String(describing: d["taskId"] ?? "")
                let dateStr = d["dateString"] as? String ?? ""
                let status = d["status"] as? String ?? "COMPLETED"
                let completedSubtasks = d["completedSubtasks"] as? [String] ?? []
                let author = d["authorName"] as? String ?? "Staff"
                let skipReason = d["skipReason"] as? String
                let timestamp = d["timestamp"] as? Int64 ?? 0
                
                let completion = TaskCompletion(
                    taskId: taskId,
                    dateString: dateStr,
                    status: status,
                    completedSubtasks: completedSubtasks,
                    authorName: author,
                    skipReason: skipReason,
                    timestamp: timestamp
                )
                map["\(taskId)_\(dateStr)"] = completion
                allList.append(completion)
            }
            self.completions = map
            self.allCompletionsList = allList.sorted { $0.timestamp > $1.timestamp }
        }
        listeners.append(completionsListener)
        
        // 6. Notes
        let notesListener = db.collection("task_notes").addSnapshotListener { [weak self] snapshot, error in
            guard let self = self, let docs = snapshot?.documents else { return }
            var list: [TaskNote] = []
            for doc in docs {
                let d = doc.data()
                list.append(TaskNote(
                    id: doc.documentID,
                    taskId: String(describing: d["taskId"] ?? ""),
                    dateString: d["dateString"] as? String ?? "",
                    authorName: d["authorName"] as? String ?? "Staff",
                    noteText: d["noteText"] as? String ?? "",
                    timestamp: d["timestamp"] as? Int64 ?? 0
                ))
            }
            self.taskNotes = list.sorted { $0.timestamp > $1.timestamp }
        }
        listeners.append(notesListener)
        
        // 7. Photos
        let photosListener = db.collection("task_photos").addSnapshotListener { [weak self] snapshot, error in
            guard let self = self, let docs = snapshot?.documents else { return }
            var list: [TaskPhoto] = []
            for doc in docs {
                let d = doc.data()
                list.append(TaskPhoto(
                    id: doc.documentID,
                    taskId: String(describing: d["taskId"] ?? ""),
                    taskTitle: d["taskTitle"] as? String ?? "Task",
                    dateString: d["dateString"] as? String ?? "",
                    authorName: d["authorName"] as? String ?? "Staff",
                    photoUri: d["photoUri"] as? String ?? "",
                    timestamp: d["timestamp"] as? Int64 ?? 0
                ))
            }
            self.taskPhotos = list.sorted { $0.timestamp > $1.timestamp }
        }
        listeners.append(photosListener)
    }
    
    public func stopListeners() {
        for l in listeners { l.remove() }
        listeners.removeAll()
    }
    
    // MARK: - PIN Security Verification
    public static func hashPin(_ pin: String, salt: String) -> String {
        let combined = pin + salt
        let digest = SHA256.hash(data: Data(combined.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
    
    public func verifyUserPin(_ user: RetailUser, pin: String) -> Bool {
        if user.pinHash.isEmpty { return false } // Needs first time setup
        let computed = Self.hashPin(pin, salt: user.pinSalt)
        return computed.lowercased() == user.pinHash.lowercased()
    }
    
    public func setUserPin(userId: String, pin: String) async throws {
        let salt = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(32).lowercased()
        let hash = Self.hashPin(pin, salt: String(salt))
        try await db.collection("users").document(userId).setData([
            "pinHash": hash,
            "pinSalt": String(salt)
        ], merge: true)
        showToast("PIN updated successfully")
    }
    
    // MARK: - Task Mutations
    public func completeTask(taskId: String, subtasks: [String]? = nil, isPartial: Bool = false) async throws {
        guard let user = currentUser else { return }
        let dateStr = selectedDateString
        let docId = "\(taskId)_\(dateStr)"
        let status = isPartial ? "PARTIAL" : "COMPLETED"
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        
        let data: [String: Any] = [
            "taskId": taskId,
            "dateString": dateStr,
            "status": status,
            "completedSubtasks": subtasks ?? [],
            "authorName": user.name,
            "timestamp": now
        ]
        
        try await db.collection("completions").document(docId).setData(data, merge: true)
        showToast(isPartial ? "Partial sign-off logged" : "Task signed off!")
    }
    
    public func skipTask(taskId: String, reason: String) async throws {
        guard let user = currentUser else { return }
        let dateStr = selectedDateString
        let docId = "\(taskId)_\(dateStr)"
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        
        let data: [String: Any] = [
            "taskId": taskId,
            "dateString": dateStr,
            "status": "SKIPPED",
            "completedSubtasks": [],
            "authorName": user.name,
            "skipReason": reason,
            "timestamp": now
        ]
        
        try await db.collection("completions").document(docId).setData(data, merge: true)
        showToast("Task skipped: \(reason)")
    }
    
    public func addNote(taskId: String, text: String) async throws {
        guard let user = currentUser else { return }
        let docRef = db.collection("task_notes").document()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        
        try await docRef.setData([
            "taskId": taskId,
            "dateString": selectedDateString,
            "authorName": user.name,
            "noteText": text,
            "timestamp": now
        ])
        showToast("Shift note saved")
    }
    
    public func addPhoto(taskId: String, taskTitle: String, base64Data: String) async throws {
        guard let user = currentUser else { return }
        let docRef = db.collection("task_photos").document()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        
        try await docRef.setData([
            "taskId": taskId,
            "taskTitle": taskTitle,
            "dateString": selectedDateString,
            "authorName": user.name,
            "photoUri": base64Data,
            "timestamp": now
        ])
        showToast("Audit photo uploaded to cloud")
    }
    
    public func saveStoreBranding(name: String, tagline: String) async throws {
        try await db.collection("store_config").document("store_name").setData(["key": "store_name", "value": name])
        try await db.collection("store_config").document("tagline").setData(["key": "tagline", "value": tagline])
        showToast("Store branding saved")
    }
}
