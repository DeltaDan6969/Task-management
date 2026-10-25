import SwiftUI

public struct HistoryView: View {
    @ObservedObject var firestore = FirestoreService.shared
    @Environment(\.colorScheme) var systemScheme
    
    @State private var filterDate: Date = Date()
    
    public var body: some View {
        let scheme = firestore.isDarkMode ? ColorScheme.dark : systemScheme
        
        ScrollView {
            VStack(spacing: 12) {
                // Date picker
                HStack {
                    DatePicker("Filter Date", selection: $filterDate, displayedComponents: .date)
                        .datePickerStyle(.compact)
                        .font(.system(size: 13, weight: .bold))
                }
                .padding(10)
                .background(ShiftTheme.surface(for: scheme))
                .cornerRadius(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
                
                // History Items
                let dateStr = formattedDate(filterDate)
                let dayCompletions = firestore.allCompletionsList.filter { $0.dateString == dateStr }
                
                if dayCompletions.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 32))
                            .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                        Text("No audit log records for this date.")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                    }
                    .padding(40)
                } else {
                    LazyVStack(spacing: 8) {
                        ForEach(dayCompletions) { comp in
                            let task = firestore.tasks.first(where: { $0.id == comp.taskId })
                            historyCard(comp, task: task, scheme: scheme)
                        }
                    }
                }
            }
            .padding(12)
        }
        .background(ShiftTheme.bg(for: scheme))
    }
    
    private func historyCard(_ comp: TaskCompletion, task: RetailTask?, scheme: ColorScheme) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(task?.title ?? "Task #\(comp.taskId)")
                    .font(.system(size: 14, weight: .black))
                    .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                Spacer()
                statusBadge(comp.status)
            }
            
            if let reason = comp.skipReason, comp.status == "SKIPPED" {
                Text("Reason: \(reason)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(ShiftTheme.orange)
            }
            
            Divider()
            
            HStack {
                Text("Logged by \(comp.authorName)")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                Spacer()
                Text(formatTime(comp.timestamp))
                    .font(.system(size: 10))
                    .foregroundColor(ShiftTheme.textSecondary(for: scheme))
            }
        }
        .padding(12)
        .background(ShiftTheme.surface(for: scheme))
        .cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
    }
    
    private func statusBadge(_ status: String) -> some View {
        let (color, text): (Color, String) = {
            if status == "COMPLETED" { return (ShiftTheme.greenDone, "DONE") }
            if status == "PARTIAL" { return (ShiftTheme.amber, "PARTIAL") }
            return (ShiftTheme.orange, "SKIPPED")
        }()
        return Text(text)
            .font(.system(size: 9, weight: .black))
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.15))
            .foregroundColor(color)
            .cornerRadius(4)
    }
    
    private func formattedDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
    
    private func formatTime(_ timestamp: Int64) -> String {
        guard timestamp > 0 else { return "" }
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp / 1000))
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
}
