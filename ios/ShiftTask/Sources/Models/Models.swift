import Foundation

public struct RetailCategory: Identifiable, Codable, Equatable {
    public var id: String
    public var name: String
    public var sortOrder: Int
    public var active: Bool
    
    public init(id: String, name: String, sortOrder: Int = 1, active: Bool = true) {
        self.id = id
        self.name = name
        self.sortOrder = sortOrder
        self.active = active
    }
}

public struct RetailTask: Identifiable, Codable, Equatable {
    public var id: String
    public var categoryId: String
    public var title: String
    public var bulletPoints: String
    public var priority: String // HIGH, MEDIUM, LOW
    public var daysOfWeek: String // ALL, WEEKDAYS, WEEKENDS, or MON,TUE...
    public var sortOrder: Int
    public var scheduleType: String? // RECURRING or ONE_TIME
    public var oneTimeDate: String?
    public var active: Bool
    
    public init(
        id: String,
        categoryId: String,
        title: String,
        bulletPoints: String = "",
        priority: String = "MEDIUM",
        daysOfWeek: String = "ALL",
        sortOrder: Int = 1,
        scheduleType: String? = "RECURRING",
        oneTimeDate: String? = nil,
        active: Bool = true
    ) {
        self.id = id
        self.categoryId = categoryId
        self.title = title
        self.bulletPoints = bulletPoints
        self.priority = priority
        self.daysOfWeek = daysOfWeek
        self.sortOrder = sortOrder
        self.scheduleType = scheduleType
        self.oneTimeDate = oneTimeDate
        self.active = active
    }
    
    public var checklistItems: [String] {
        bulletPoints
            .components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}

public struct RetailUser: Identifiable, Codable, Equatable {
    public var id: String
    public var name: String
    public var initials: String
    public var role: String // ADMIN or STAFF
    public var pinHash: String
    public var pinSalt: String
    public var active: Bool
    
    public init(
        id: String,
        name: String,
        initials: String,
        role: String = "STAFF",
        pinHash: String = "",
        pinSalt: String = "",
        active: Bool = true
    ) {
        self.id = id
        self.name = name
        self.initials = initials
        self.role = role
        self.pinHash = pinHash
        self.pinSalt = pinSalt
        self.active = active
    }
    
    public var isAdmin: Bool {
        role.uppercased() == "ADMIN"
    }
}

public struct TaskCompletion: Identifiable, Codable, Equatable {
    public var id: String { "\(taskId)_\(dateString)" }
    public var taskId: String
    public var dateString: String
    public var status: String // COMPLETED, PARTIAL, SKIPPED
    public var completedSubtasks: [String]
    public var authorName: String
    public var skipReason: String?
    public var timestamp: Int64
    
    public init(
        taskId: String,
        dateString: String,
        status: String,
        completedSubtasks: [String] = [],
        authorName: String,
        skipReason: String? = nil,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.taskId = taskId
        self.dateString = dateString
        self.status = status
        self.completedSubtasks = completedSubtasks
        self.authorName = authorName
        self.skipReason = skipReason
        self.timestamp = timestamp
    }
}

public struct TaskNote: Identifiable, Codable, Equatable {
    public var id: String
    public var taskId: String
    public var dateString: String
    public var authorName: String
    public var noteText: String
    public var timestamp: Int64
    
    public init(
        id: String = UUID().uuidString,
        taskId: String,
        dateString: String,
        authorName: String,
        noteText: String,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.taskId = taskId
        self.dateString = dateString
        self.authorName = authorName
        self.noteText = noteText
        self.timestamp = timestamp
    }
}

public struct TaskPhoto: Identifiable, Codable, Equatable {
    public var id: String
    public var taskId: String
    public var taskTitle: String
    public var dateString: String
    public var authorName: String
    public var photoUri: String
    public var timestamp: Int64
    
    public init(
        id: String = UUID().uuidString,
        taskId: String,
        taskTitle: String,
        dateString: String,
        authorName: String,
        photoUri: String,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.taskId = taskId
        self.taskTitle = taskTitle
        self.dateString = dateString
        self.authorName = authorName
        self.photoUri = photoUri
        self.timestamp = timestamp
    }
}

public struct StoreConfig: Codable, Equatable {
    public var storeName: String
    public var tagline: String
    
    public init(
        storeName: String = "ONESTOP CANTERBURY",
        tagline: String = "SIGN IN TO START YOUR SHIFT"
    ) {
        self.storeName = storeName
        self.tagline = tagline
    }
}
