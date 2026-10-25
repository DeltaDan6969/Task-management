import SwiftUI

public struct TodayView: View {
    @ObservedObject var firestore = FirestoreService.shared
    @Environment(\.colorScheme) var systemScheme
    
    @State private var activeCategoryFilter: String = "ALL"
    @State private var selectedTaskForSubtasks: RetailTask? = nil
    @State private var selectedSubtaskChecks: Set<String> = []
    
    // Skip Task Modal
    @State private var selectedTaskForSkip: RetailTask? = nil
    @State private var selectedSkipPreset: String = "Not needed"
    @State private var customSkipReason: String = ""
    
    // Notes Modal
    @State private var selectedTaskForNotes: RetailTask? = nil
    @State private var newNoteText: String = ""
    
    // Photo Modal
    @State private var selectedTaskForPhoto: RetailTask? = nil
    @State private var showPhotoPicker = false
    
    let skipPresets = [
        "Not needed",
        "Crisps Worked Instead",
        "Grocery Worked Instead",
        "Frozen Worked Instead",
        "Not Enough Time",
        "N.A"
    ]
    
    public var body: some View {
        let scheme = firestore.isDarkMode ? ColorScheme.dark : systemScheme
        
        ScrollView {
            VStack(spacing: 12) {
                // Date Bar
                HStack {
                    Button {
                        changeDate(by: -1)
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 13, weight: .bold))
                            .frame(width: 32, height: 32)
                            .background(ShiftTheme.surfaceSubtle(for: scheme))
                            .cornerRadius(6)
                    }
                    
                    Spacer()
                    
                    Text(formattedSelectedDate)
                        .font(.system(size: 12, weight: .black))
                        .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                    
                    Spacer()
                    
                    Button("TODAY") {
                        firestore.selectedDate = Date()
                    }
                    .font(.system(size: 10, weight: .black))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(ShiftTheme.orange)
                    .foregroundColor(.white)
                    .cornerRadius(4)
                    
                    Button {
                        changeDate(by: 1)
                    } label: {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .bold))
                            .frame(width: 32, height: 32)
                            .background(ShiftTheme.surfaceSubtle(for: scheme))
                            .cornerRadius(6)
                    }
                }
                .padding(10)
                .background(ShiftTheme.surface(for: scheme))
                .cornerRadius(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
                
                // Shift KPI Mini Bar
                HStack(spacing: 8) {
                    kpiTile(title: "COMPLETED", count: completedCount, color: ShiftTheme.greenDone, scheme: scheme)
                    kpiTile(title: "PARTIAL", count: partialCount, color: ShiftTheme.amber, scheme: scheme)
                    kpiTile(title: "PENDING", count: pendingCount, color: ShiftTheme.blue, scheme: scheme)
                }
                
                // Category Filter Chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        filterChip(title: "ALL", isSelected: activeCategoryFilter == "ALL", scheme: scheme) {
                            activeCategoryFilter = "ALL"
                        }
                        ForEach(firestore.categories) { cat in
                            filterChip(title: cat.name, isSelected: activeCategoryFilter == cat.id, scheme: scheme) {
                                activeCategoryFilter = cat.id
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }
                
                // Grouped Tasks
                let groups = groupedTasks()
                if groups.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "checkmark.circle")
                            .font(.system(size: 32))
                            .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                        Text("No tasks scheduled for today.")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                    }
                    .padding(30)
                } else {
                    ForEach(groups, id: \.category.id) { group in
                        VStack(alignment: .leading, spacing: 8) {
                            // Category Header
                            HStack {
                                Text(group.category.name)
                                    .font(.system(size: 13, weight: .black))
                                    .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                                Spacer()
                                Text("\(group.tasks.count) TASKS")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                            }
                            .padding(.bottom, 2)
                            .overlay(Rectangle().frame(height: 2).foregroundColor(ShiftTheme.orange), alignment: .bottom)
                            
                            ForEach(group.tasks) { task in
                                taskCard(task, scheme: scheme)
                            }
                        }
                        .padding(.top, 6)
                    }
                }
            }
            .padding(12)
        }
        .background(ShiftTheme.bg(for: scheme))
        .sheet(item: $selectedTaskForSubtasks) { task in
            subtasksSheet(for: task, scheme: scheme)
                .presentationDetents([.medium, .large])
        }
        .sheet(item: $selectedTaskForSkip) { task in
            skipTaskSheet(for: task, scheme: scheme)
                .presentationDetents([.medium, .large])
        }
        .sheet(item: $selectedTaskForNotes) { task in
            notesSheet(for: task, scheme: scheme)
                .presentationDetents([.medium, .large])
        }
        .sheet(item: $selectedTaskForPhoto) { task in
            PhotoAuditView(task: task)
        }
    }
    
    // MARK: - Task Card
    private func taskCard(_ task: RetailTask, scheme: ColorScheme) -> some View {
        let key = "\(task.id)_\(firestore.selectedDateString)"
        let completion = firestore.completions[key]
        let status = completion?.status ?? "PENDING"
        
        let cardBg: Color = {
            if status == "COMPLETED" {
                return scheme == .dark ? Color(red: 0.05, green: 0.12, blue: 0.08) : Color(red: 0.98, green: 1.0, blue: 0.98)
            } else if status == "PARTIAL" {
                return scheme == .dark ? Color(red: 0.14, green: 0.11, blue: 0.06) : Color(red: 1.0, green: 0.99, blue: 0.96)
            } else if status == "SKIPPED" {
                return scheme == .dark ? Color(red: 0.14, green: 0.08, blue: 0.05) : Color(red: 1.0, green: 0.97, blue: 0.95)
            }
            return ShiftTheme.surface(for: scheme)
        }()
        
        let borderLeftColor: Color = {
            if status == "COMPLETED" { return ShiftTheme.greenDone }
            if status == "PARTIAL" { return ShiftTheme.amber }
            if status == "SKIPPED" { return ShiftTheme.orange }
            return Color.clear
        }()
        
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(task.title)
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                    
                    HStack(spacing: 4) {
                        priorityBadge(task.priority)
                        
                        if let sched = task.scheduleType, sched == "ONE_TIME" {
                            Text("★ ONE-TIME")
                                .font(.system(size: 9, weight: .black))
                                .padding(.horizontal, 4)
                                .padding(.vertical, 2)
                                .background(Color.purple.opacity(0.15))
                                .foregroundColor(.purple)
                                .cornerRadius(3)
                        }
                    }
                }
                Spacer()
                
                if status == "COMPLETED" {
                    Label("DONE", systemImage: "checkmark.circle.fill")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(ShiftTheme.greenDone)
                } else if status == "PARTIAL" {
                    Label("PARTIAL", systemImage: "clock.fill")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(ShiftTheme.amber)
                } else if status == "SKIPPED" {
                    Label("SKIPPED", systemImage: "exclamationmark.triangle.fill")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(ShiftTheme.orange)
                }
            }
            
            // Subtasks preview or skip note
            if let skipReason = completion?.skipReason, status == "SKIPPED" {
                Text("Skipped: \(skipReason)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(ShiftTheme.orange)
                    .padding(6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.orange.opacity(0.1))
                    .cornerRadius(4)
            } else if !task.bulletPoints.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(task.checklistItems.prefix(3), id: \.self) { bullet in
                        HStack(alignment: .top, spacing: 4) {
                            Text("•")
                                .font(.system(size: 11, weight: .black))
                                .foregroundColor(ShiftTheme.orange)
                            Text(bullet)
                                .font(.system(size: 11))
                                .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                        }
                    }
                }
                .padding(6)
                .background(ShiftTheme.surfaceSubtle(for: scheme))
                .cornerRadius(4)
            }
            
            // Audit author trail
            if let author = completion?.authorName {
                Text("Signed off by \(author)")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(ShiftTheme.textSecondary(for: scheme))
            }
            
            // Action Buttons
            HStack(spacing: 6) {
                if !task.checklistItems.isEmpty {
                    Button {
                        selectedTaskForSubtasks = task
                        selectedSubtaskChecks = Set(completion?.completedSubtasks ?? [])
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "checklist")
                            Text("CHECKLIST")
                        }
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 36)
                        .background(status == "COMPLETED" ? ShiftTheme.greenDone : ShiftTheme.headerBlack(for: scheme))
                        .cornerRadius(6)
                    }
                } else {
                    Button {
                        Task {
                            try? await firestore.completeTask(taskId: task.id)
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "checkmark")
                            Text("SIGN OFF")
                        }
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 36)
                        .background(status == "COMPLETED" ? ShiftTheme.greenDone : ShiftTheme.headerBlack(for: scheme))
                        .cornerRadius(6)
                    }
                }
                
                Button {
                    selectedTaskForSkip = task
                    selectedSkipPreset = "Not needed"
                    customSkipReason = ""
                } label: {
                    HStack(spacing: 2) {
                        Image(systemName: "exclamationmark.triangle")
                        Text("SKIP")
                    }
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(ShiftTheme.orange)
                    .frame(width: 65, height: 36)
                    .background(ShiftTheme.orangeLight.opacity(0.4))
                    .cornerRadius(6)
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(ShiftTheme.orange.opacity(0.5), lineWidth: 1))
                }
                
                Button {
                    selectedTaskForNotes = task
                    newNoteText = ""
                } label: {
                    Image(systemName: "note.text")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                        .frame(width: 36, height: 36)
                        .background(ShiftTheme.surfaceSubtle(for: scheme))
                        .cornerRadius(6)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
                }
                
                Button {
                    selectedTaskForPhoto = task
                } label: {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                        .frame(width: 36, height: 36)
                        .background(ShiftTheme.surfaceSubtle(for: scheme))
                        .cornerRadius(6)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
                }
            }
        }
        .padding(12)
        .background(cardBg)
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(ShiftTheme.border(for: scheme), lineWidth: 1)
        )
        .overlay(
            Rectangle()
                .frame(width: 5)
                .foregroundColor(borderLeftColor)
                .cornerRadius(10),
            alignment: .leading
        )
    }
    
    // MARK: - Subtasks Checklist Sheet
    private func subtasksSheet(for task: RetailTask, scheme: ColorScheme) -> some View {
        VStack(spacing: 14) {
            Capsule()
                .fill(Color.gray.opacity(0.5))
                .frame(width: 40, height: 4)
                .padding(.top, 8)
            
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(task.title)
                        .font(.system(size: 15, weight: .black))
                    Text("CHECKLIST AUDIT SIGN-OFF")
                        .font(.system(size: 10, weight: .heavy))
                        .foregroundColor(ShiftTheme.orange)
                }
                Spacer()
                Button("Close") {
                    selectedTaskForSubtasks = nil
                }
            }
            
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(task.checklistItems, id: \.self) { item in
                        Button {
                            if selectedSubtaskChecks.contains(item) {
                                selectedSubtaskChecks.remove(item)
                            } else {
                                selectedSubtaskChecks.insert(item)
                            }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: selectedSubtaskChecks.contains(item) ? "checkmark.square.fill" : "square")
                                    .font(.system(size: 20))
                                    .foregroundColor(selectedSubtaskChecks.contains(item) ? ShiftTheme.greenDone : .gray)
                                Text(item)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                                    .strikethrough(selectedSubtaskChecks.contains(item))
                                Spacer()
                            }
                            .padding(10)
                            .background(ShiftTheme.surfaceSubtle(for: scheme))
                            .cornerRadius(8)
                        }
                    }
                }
            }
            
            HStack(spacing: 8) {
                Button("PARTIAL SIGN-OFF") {
                    Task {
                        try? await firestore.completeTask(taskId: task.id, subtasks: Array(selectedSubtaskChecks), isPartial: true)
                        selectedTaskForSubtasks = nil
                    }
                }
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(ShiftTheme.amber)
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(ShiftTheme.amberLight)
                .cornerRadius(8)
                
                Button("SIGN OFF ALL") {
                    Task {
                        try? await firestore.completeTask(taskId: task.id, subtasks: task.checklistItems, isPartial: false)
                        selectedTaskForSubtasks = nil
                    }
                }
                .font(.system(size: 11, weight: .black))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(ShiftTheme.greenDone)
                .cornerRadius(8)
            }
        }
        .padding(16)
        .background(ShiftTheme.surface(for: scheme))
    }
    
    // MARK: - Skip Task Sheet
    private func skipTaskSheet(for task: RetailTask, scheme: ColorScheme) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Capsule()
                .fill(Color.gray.opacity(0.5))
                .frame(width: 40, height: 4)
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
            
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(ShiftTheme.orange)
                Text("SKIP TASK")
                    .font(.system(size: 16, weight: .black))
            }
            
            Text(task.title)
                .font(.system(size: 13, weight: .bold))
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(ShiftTheme.surfaceSubtle(for: scheme))
                .cornerRadius(6)
            
            Text("SELECT REASON FOR SKIPPING:")
                .font(.system(size: 11, weight: .black))
            
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                ForEach(skipPresets, id: \.self) { preset in
                    Button {
                        selectedSkipPreset = preset
                    } label: {
                        Text(preset)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(selectedSkipPreset == preset ? ShiftTheme.orange : ShiftTheme.textPrimary(for: scheme))
                            .padding(8)
                            .frame(maxWidth: .infinity)
                            .background(selectedSkipPreset == preset ? ShiftTheme.orangeLight : ShiftTheme.surfaceSubtle(for: scheme))
                            .cornerRadius(6)
                            .overlay(RoundedRectangle(cornerRadius: 6).stroke(selectedSkipPreset == preset ? ShiftTheme.orange : ShiftTheme.border(for: scheme), lineWidth: 1))
                    }
                }
            }
            
            Text("OR ENTER SPECIFIC REASON:")
                .font(.system(size: 10, weight: .heavy))
                .foregroundColor(ShiftTheme.textSecondary(for: scheme))
            
            TextField("e.g. Refrigeration unit serviced", text: $customSkipReason)
                .textFieldStyle(.roundedBorder)
            
            HStack(spacing: 8) {
                Button("CANCEL") {
                    selectedTaskForSkip = nil
                }
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(ShiftTheme.surfaceSubtle(for: scheme))
                .cornerRadius(8)
                
                Button("CONFIRM SKIP") {
                    let reason = customSkipReason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? selectedSkipPreset : customSkipReason
                    Task {
                        try? await firestore.skipTask(taskId: task.id, reason: reason)
                        selectedTaskForSkip = nil
                    }
                }
                .font(.system(size: 12, weight: .black))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(ShiftTheme.orange)
                .cornerRadius(8)
            }
        }
        .padding(16)
        .background(ShiftTheme.surface(for: scheme))
    }
    
    // MARK: - Notes Sheet
    private func notesSheet(for task: RetailTask, scheme: ColorScheme) -> some View {
        VStack(spacing: 12) {
            Capsule()
                .fill(Color.gray.opacity(0.5))
                .frame(width: 40, height: 4)
                .padding(.top, 8)
            
            Text("Shift Observation Notes")
                .font(.system(size: 15, weight: .black))
            
            let notes = firestore.taskNotes.filter { $0.taskId == task.id }
            ScrollView {
                if notes.isEmpty {
                    Text("No notes recorded yet for this task.")
                        .font(.system(size: 11))
                        .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                        .padding(20)
                } else {
                    VStack(spacing: 6) {
                        ForEach(notes) { note in
                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text(note.authorName)
                                        .font(.system(size: 10, weight: .black))
                                    Spacer()
                                    Text(note.dateString)
                                        .font(.system(size: 9))
                                }
                                Text(note.noteText)
                                    .font(.system(size: 12))
                            }
                            .padding(8)
                            .background(ShiftTheme.surfaceSubtle(for: scheme))
                            .cornerRadius(6)
                        }
                    }
                }
            }
            
            TextField("Add a shift note...", text: $newNoteText)
                .textFieldStyle(.roundedBorder)
            
            Button("SAVE NOTE") {
                guard !newNoteText.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                Task {
                    try? await firestore.addNote(taskId: task.id, text: newNoteText)
                    newNoteText = ""
                    selectedTaskForNotes = nil
                }
            }
            .font(.system(size: 12, weight: .black))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 40)
            .background(ShiftTheme.greenDone)
            .cornerRadius(8)
        }
        .padding(16)
        .background(ShiftTheme.surface(for: scheme))
    }
    
    // MARK: - Helpers
    private func changeDate(by days: Int) {
        if let newDate = Calendar.current.date(byAdding: .day, value: days, to: firestore.selectedDate) {
            firestore.selectedDate = newDate
        }
    }
    
    private var formattedSelectedDate: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, d MMM yyyy"
        return formatter.string(from: firestore.selectedDate).uppercased()
    }
    
    private func kpiTile(title: String, count: Int, color: Color, scheme: ColorScheme) -> some View {
        VStack(spacing: 2) {
            Text("\(count)")
                .font(.system(size: 18, weight: .black))
                .foregroundColor(color)
            Text(title)
                .font(.system(size: 9, weight: .heavy))
                .foregroundColor(ShiftTheme.textSecondary(for: scheme))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(ShiftTheme.surface(for: scheme))
        .cornerRadius(8)
        .overlay(Rectangle().frame(height: 3).foregroundColor(color), alignment: .bottom)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
    }
    
    private func filterChip(title: String, isSelected: Bool, scheme: ColorScheme, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11, weight: .heavy))
                .foregroundColor(isSelected ? .white : ShiftTheme.textSecondary(for: scheme))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? ShiftTheme.orange : ShiftTheme.surface(for: scheme))
                .cornerRadius(14)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(isSelected ? ShiftTheme.orange : ShiftTheme.border(for: scheme), lineWidth: 1))
        }
    }
    
    private func priorityBadge(_ priority: String) -> some View {
        let color: Color = {
            if priority == "HIGH" { return .red }
            if priority == "MEDIUM" { return .orange }
            return .blue
        }()
        return Text(priority)
            .font(.system(size: 9, weight: .black))
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
            .background(color.opacity(0.15))
            .foregroundColor(color)
            .cornerRadius(3)
    }
    
    private func groupedTasks() -> [(category: RetailCategory, tasks: [RetailTask])] {
        var filtered = firestore.tasks
        if activeCategoryFilter != "ALL" {
            filtered = filtered.filter { $0.categoryId == activeCategoryFilter }
        }
        
        var result: [(category: RetailCategory, tasks: [RetailTask])] = []
        for cat in firestore.categories {
            let catTasks = filtered.filter { $0.categoryId == cat.id }
            if !catTasks.isEmpty {
                result.append((category: cat, tasks: catTasks))
            }
        }
        return result
    }
    
    private var completedCount: Int {
        let dateStr = firestore.selectedDateString
        return firestore.completions.values.filter { $0.dateString == dateStr && $0.status == "COMPLETED" }.count
    }
    
    private var partialCount: Int {
        let dateStr = firestore.selectedDateString
        return firestore.completions.values.filter { $0.dateString == dateStr && $0.status == "PARTIAL" }.count
    }
    
    private var pendingCount: Int {
        let total = firestore.tasks.count
        return max(0, total - completedCount - partialCount)
    }
}
