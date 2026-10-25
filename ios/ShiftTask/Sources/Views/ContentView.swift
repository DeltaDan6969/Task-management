import SwiftUI

public struct ContentView: View {
    @ObservedObject var firestore = FirestoreService.shared
    @Environment(\.colorScheme) var systemScheme
    
    @State private var selectedTab = 0
    
    public var body: some View {
        let scheme = firestore.isDarkMode ? ColorScheme.dark : systemScheme
        
        ZStack {
            ShiftTheme.bg(for: scheme).ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Top Mobile Header
                HStack(spacing: 10) {
                    HStack(spacing: 8) {
                        Image(systemName: "storefront.fill")
                            .font(.system(size: 16))
                            .foregroundColor(ShiftTheme.orange)
                            .frame(width: 32, height: 32)
                            .background(ShiftTheme.surfaceSubtle(for: scheme))
                            .cornerRadius(6)
                        
                        VStack(alignment: .leading, spacing: 1) {
                            Text(firestore.storeConfig.storeName)
                                .font(.system(size: 13, weight: .black))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            Text("SHIFT TERMINAL")
                                .font(.system(size: 8, weight: .heavy))
                                .foregroundColor(ShiftTheme.orange)
                        }
                    }
                    
                    Spacer()
                    
                    // Night Shift Theme Toggle Button
                    Button {
                        firestore.toggleTheme()
                    } label: {
                        Image(systemName: firestore.isDarkMode ? "sun.max.fill" : "moon.fill")
                            .font(.system(size: 13))
                            .foregroundColor(.white)
                            .frame(width: 30, height: 30)
                            .background(Color.white.opacity(0.15))
                            .cornerRadius(6)
                    }
                    
                    // Cloud Status Pill
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.green)
                            .frame(width: 6, height: 6)
                        Text("LIVE")
                            .font(.system(size: 9, weight: .black))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.12))
                    .cornerRadius(10)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(ShiftTheme.headerBlack(for: scheme))
                .overlay(Rectangle().frame(height: 2).foregroundColor(ShiftTheme.orange), alignment: .bottom)
                
                // Active User Session Bar
                if let user = firestore.currentUser {
                    HStack {
                        ZStack {
                            Circle()
                                .fill(ShiftTheme.orange)
                                .frame(width: 20, height: 20)
                            Text(user.initials)
                                .font(.system(size: 9, weight: .black))
                                .foregroundColor(.white)
                        }
                        Text(user.name)
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(.white)
                        Text("•")
                            .foregroundColor(ShiftTheme.orange)
                        Text(user.role)
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.gray)
                        Spacer()
                        Button {
                            firestore.signOut()
                        } label: {
                            HStack(spacing: 2) {
                                Image(systemName: "rectangle.portrait.and.arrow.right")
                                Text("LOG OUT")
                            }
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundColor(.red)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Color.red.opacity(0.15))
                            .cornerRadius(4)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Color(red: 0.11, green: 0.12, blue: 0.14))
                }
                
                // Main Screen Content
                if firestore.currentUser == nil {
                    KioskSignInView()
                } else {
                    TabView(selection: $selectedTab) {
                        TodayView()
                            .tabItem {
                                Label("Today", systemImage: "checklist")
                            }
                            .tag(0)
                        
                        HistoryView()
                            .tabItem {
                                Label("History", systemImage: "clock.arrow.circlepath")
                            }
                            .tag(1)
                        
                        AdminView()
                            .tabItem {
                                Label("Admin", systemImage: "person.badge.shield.checkmark.fill")
                            }
                            .tag(2)
                    }
                    .tint(ShiftTheme.orange)
                }
            }
            
            // Floating Toast Notification
            if let toast = firestore.toastMessage {
                VStack {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(Color.green)
                        Text(toast)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.black.opacity(0.85))
                    .cornerRadius(20)
                    .shadow(radius: 8)
                    .padding(.top, 50)
                    
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .animation(.easeInOut, value: firestore.toastMessage)
            }
        }
        .preferredColorScheme(firestore.isDarkMode ? .dark : .light)
    }
}
