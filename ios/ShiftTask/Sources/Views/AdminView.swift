import SwiftUI

public struct AdminView: View {
    @ObservedObject var firestore = FirestoreService.shared
    @Environment(\.colorScheme) var systemScheme
    
    @State private var activeTab: AdminTab = .report
    
    // Branding Form
    @State private var storeNameInput: String = ""
    @State private var taglineInput: String = ""
    
    // Monthly KPI Report
    @State private var reportMonthOffset: Int = 0
    @State private var showExportShare = false
    @State private var csvExportString: String = ""
    
    enum AdminTab: String, CaseIterable {
        case report = "KPI REPORT"
        case tasks = "TASKS"
        case categories = "CATEGORIES"
        case staff = "STAFF"
        case branding = "SETTINGS"
    }
    
    public var body: some View {
        let scheme = firestore.isDarkMode ? ColorScheme.dark : systemScheme
        
        ScrollView {
            VStack(spacing: 14) {
                // Admin Subtabs Carousel
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(AdminTab.allCases, id: \.self) { tab in
                            Button {
                                activeTab = tab
                            } label: {
                                Text(tab.rawValue)
                                    .font(.system(size: 11, weight: .black))
                                    .foregroundColor(activeTab == tab ? .white : ShiftTheme.textSecondary(for: scheme))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(activeTab == tab ? ShiftTheme.headerBlack(for: scheme) : ShiftTheme.surface(for: scheme))
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
                            }
                        }
                    }
                }
                
                switch activeTab {
                case .report:
                    monthlyReportTab(scheme: scheme)
                case .tasks:
                    tasksTab(scheme: scheme)
                case .categories:
                    categoriesTab(scheme: scheme)
                case .staff:
                    staffTab(scheme: scheme)
                case .branding:
                    settingsTab(scheme: scheme)
                }
            }
            .padding(12)
        }
        .background(ShiftTheme.bg(for: scheme))
        .onAppear {
            storeNameInput = firestore.storeConfig.storeName
            taglineInput = firestore.storeConfig.tagline
        }
        .sheet(isPresented: $showExportShare) {
            ActivityView(activityItems: [csvExportString])
        }
    }
    
    // MARK: - Monthly Report & CSV Tab
    private func monthlyReportTab(scheme: ColorScheme) -> some View {
        let monthDate = Calendar.current.date(byAdding: .month, value: reportMonthOffset, to: Date()) ?? Date()
        let monthFormatter = DateFormatter()
        monthFormatter.dateFormat = "MMMM yyyy"
        let monthTitle = monthFormatter.string(from: monthDate)
        
        let monthPrefixFormatter = DateFormatter()
        monthPrefixFormatter.dateFormat = "yyyy-MM"
        let monthPrefix = monthPrefixFormatter.string(from: monthDate)
        
        let monthlyCompletions = firestore.allCompletionsList.filter { $0.dateString.hasPrefix(monthPrefix) }
        let completed = monthlyCompletions.filter { $0.status == "COMPLETED" }.count
        let skipped = monthlyCompletions.filter { $0.status == "SKIPPED" }.count
        let total = completed + skipped
        let compliance = total > 0 ? Int((Double(completed) / Double(total)) * 100) : 0
        
        return VStack(spacing: 12) {
            // Month Header Card
            VStack(spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("MONTHLY PERFORMANCE & AUDIT")
                            .font(.system(size: 10, weight: .heavy))
                            .foregroundColor(.gray)
                        Text(monthTitle)
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(.white)
                    }
                    Spacer()
                    HStack(spacing: 4) {
                        Button {
                            reportMonthOffset -= 1
                        } label: {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 12, weight: .bold))
                                .frame(width: 30, height: 30)
                                .background(Color.white.opacity(0.15))
                                .foregroundColor(.white)
                                .cornerRadius(4)
                        }
                        Button {
                            reportMonthOffset += 1
                        } label: {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .bold))
                                .frame(width: 30, height: 30)
                                .background(Color.white.opacity(0.15))
                                .foregroundColor(.white)
                                .cornerRadius(4)
                        }
                    }
                }
                
                HStack(spacing: 8) {
                    metricCard(title: "COMPLETED", val: "\(completed)", color: ShiftTheme.greenDone)
                    metricCard(title: "SKIPPED", val: "\(skipped)", color: ShiftTheme.orange)
                    metricCard(title: "COMPLIANCE", val: "\(compliance)%", color: ShiftTheme.blue)
                }
                
                Button {
                    generateAndShareCSV(monthTitle: monthTitle, monthPrefix: monthPrefix)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "square.and.arrow.up.fill")
                        Text("EXPORT CSV REPORT (\(monthTitle.uppercased()))")
                    }
                    .font(.system(size: 12, weight: .black))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 42)
                    .background(ShiftTheme.orange)
                    .cornerRadius(6)
                }
            }
            .padding(14)
            .background(ShiftTheme.headerBlack(for: scheme))
            .cornerRadius(10)
            
            // Skip Reasons Breakdown
            let skips = monthlyCompletions.filter { $0.status == "SKIPPED" }
            VStack(alignment: .leading, spacing: 8) {
                Text("SKIP REASONS BREAKDOWN (\(skips.count))")
                    .font(.system(size: 12, weight: .black))
                    .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                
                if skips.isEmpty {
                    Text("No skipped tasks recorded for this month.")
                        .font(.system(size: 11))
                        .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                        .padding(.vertical, 8)
                } else {
                    let grouped = Dictionary(grouping: skips, by: { $0.skipReason ?? "Unspecified" })
                    ForEach(grouped.keys.sorted(), id: \.self) { reason in
                        HStack {
                            Text(reason)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                            Spacer()
                            Text("\(grouped[reason]?.count ?? 0) times")
                                .font(.system(size: 11, weight: .black))
                                .foregroundColor(ShiftTheme.orange)
                        }
                        .padding(8)
                        .background(ShiftTheme.surfaceSubtle(for: scheme))
                        .cornerRadius(6)
                    }
                }
            }
            .padding(12)
            .background(ShiftTheme.surface(for: scheme))
            .cornerRadius(10)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
        }
    }
    
    private func metricCard(title: String, val: String, color: Color) -> some View {
        VStack(spacing: 2) {
            Text(val)
                .font(.system(size: 18, weight: .black))
                .foregroundColor(color)
            Text(title)
                .font(.system(size: 8, weight: .heavy))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(Color.white.opacity(0.08))
        .cornerRadius(6)
    }
    
    private func generateAndShareCSV(monthTitle: String, monthPrefix: String) {
        var csv = "ShiftTask Retail Performance Report - \(monthTitle)\n"
        csv += "Date,Task ID,Task Title,Category,Status,Signed Off By,Skip Reason,Timestamp\n"
        
        let comps = firestore.allCompletionsList.filter { $0.dateString.hasPrefix(monthPrefix) }
        for c in comps {
            let t = firestore.tasks.first(where: { $0.id == c.taskId })
            let cat = firestore.categories.first(where: { $0.id == t?.categoryId })
            let tTitle = (t?.title ?? "Task #\(c.taskId)").replacingOccurrences(of: ",", with: ";")
            let catName = (cat?.name ?? "").replacingOccurrences(of: ",", with: ";")
            let reason = (c.skipReason ?? "").replacingOccurrences(of: ",", with: ";")
            
            csv += "\(c.dateString),\(c.taskId),\"\(tTitle)\",\"\(catName)\",\(c.status),\"\(c.authorName)\",\"\(reason)\",\(c.timestamp)\n"
        }
        
        csvExportString = csv
        showExportShare = true
    }
    
    // MARK: - Tasks Tab
    private func tasksTab(scheme: ColorScheme) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("TOTAL CONFIGURED TASKS: \(firestore.tasks.count)")
                .font(.system(size: 11, weight: .black))
                .foregroundColor(ShiftTheme.textSecondary(for: scheme))
            
            ForEach(firestore.tasks) { task in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(task.title)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                        Text(task.daysOfWeek)
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                    }
                    Spacer()
                    Text(task.priority)
                        .font(.system(size: 9, weight: .black))
                        .padding(4)
                        .background(Color.orange.opacity(0.15))
                        .foregroundColor(.orange)
                        .cornerRadius(4)
                }
                .padding(10)
                .background(ShiftTheme.surface(for: scheme))
                .cornerRadius(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
            }
        }
    }
    
    // MARK: - Categories Tab
    private func categoriesTab(scheme: ColorScheme) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(firestore.categories) { cat in
                HStack {
                    Text(cat.name)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                    Spacer()
                    Text("Order #\(cat.sortOrder)")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                }
                .padding(10)
                .background(ShiftTheme.surface(for: scheme))
                .cornerRadius(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
            }
        }
    }
    
    // MARK: - Staff Tab
    private func staffTab(scheme: ColorScheme) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(firestore.users) { user in
                HStack {
                    ZStack {
                        Circle()
                            .fill(ShiftTheme.headerBlack(for: scheme))
                            .frame(width: 32, height: 32)
                        Text(user.initials)
                            .font(.system(size: 12, weight: .black))
                            .foregroundColor(.white)
                    }
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(user.name)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                        Text(user.role)
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundColor(user.isAdmin ? .red : .blue)
                    }
                    Spacer()
                    Text(user.pinHash.isEmpty ? "PIN Not Set" : "PIN Active")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(user.pinHash.isEmpty ? .purple : .green)
                }
                .padding(10)
                .background(ShiftTheme.surface(for: scheme))
                .cornerRadius(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
            }
        }
    }
    
    // MARK: - Settings Tab
    private func settingsTab(scheme: ColorScheme) -> some View {
        VStack(spacing: 12) {
            // Theme toggle card
            VStack(alignment: .leading, spacing: 10) {
                Text("DISPLAY & NIGHT SHIFT")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Dark Theme (Night Shift)")
                            .font(.system(size: 13, weight: .black))
                            .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                        Text("Reduces glare during low-light store operations")
                            .font(.system(size: 10))
                            .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                    }
                    Spacer()
                    Toggle("", isOn: Binding(
                        get: { firestore.isDarkMode },
                        set: { _ in firestore.toggleTheme() }
                    ))
                    .labelsHidden()
                }
            }
            .padding(12)
            .background(ShiftTheme.surface(for: scheme))
            .cornerRadius(10)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
            
            // Store Branding Card
            VStack(alignment: .leading, spacing: 10) {
                Text("STORE BRANDING")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("STORE NAME")
                        .font(.system(size: 9, weight: .heavy))
                        .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                    TextField("Store Name", text: $storeNameInput)
                        .textFieldStyle(.roundedBorder)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("STORE TAGLINE")
                        .font(.system(size: 9, weight: .heavy))
                        .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                    TextField("Tagline", text: $taglineInput)
                        .textFieldStyle(.roundedBorder)
                }
                
                Button("SAVE BRANDING") {
                    Task {
                        try? await firestore.saveStoreBranding(name: storeNameInput, tagline: taglineInput)
                    }
                }
                .font(.system(size: 12, weight: .black))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(ShiftTheme.greenDone)
                .cornerRadius(6)
            }
            .padding(12)
            .background(ShiftTheme.surface(for: scheme))
            .cornerRadius(10)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
        }
    }
}

// Activity View wrapper for sharing CSV
struct ActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
