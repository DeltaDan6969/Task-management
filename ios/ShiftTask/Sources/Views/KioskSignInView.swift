import SwiftUI

public struct KioskSignInView: View {
    @ObservedObject var firestore = FirestoreService.shared
    @Environment(\.colorScheme) var systemScheme
    
    @State private var selectedUserForPin: RetailUser? = nil
    @State private var pinCode: String = ""
    @State private var pinError: String? = nil
    @State private var showFirstTimePinSetup = false
    @State private var newPin1: String = ""
    @State private var newPin2: String = ""
    @State private var firstTimeError: String? = nil
    
    // Shift Clock
    @State private var currentTimeStr = ""
    @State private var currentDateStr = ""
    let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    
    public var body: some View {
        let scheme = firestore.isDarkMode ? ColorScheme.dark : systemScheme
        
        ScrollView {
            VStack(spacing: 16) {
                // Kiosk Hero Card with Shift Clock
                VStack(spacing: 12) {
                    HStack(spacing: 12) {
                        Image(systemName: "building.2.crop.circle.fill")
                            .resizable()
                            .frame(width: 36, height: 36)
                            .foregroundColor(ShiftTheme.orange)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(firestore.storeConfig.storeName)
                                .font(.system(size: 16, weight: .black))
                                .foregroundColor(.white)
                            Text(firestore.storeConfig.tagline)
                                .font(.system(size: 10, weight: .heavy))
                                .foregroundColor(ShiftTheme.orange)
                        }
                        Spacer()
                    }
                    
                    // Shift Clock
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Image(systemName: "clock.fill")
                                    .font(.system(size: 11))
                                Text("SHIFT CLOCK")
                                    .font(.system(size: 10, weight: .black))
                            }
                            .foregroundColor(ShiftTheme.orange)
                            
                            Text(currentDateStr.uppercased())
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.white)
                        }
                        Spacer()
                        Text(currentTimeStr)
                            .font(.system(size: 20, weight: .black, design: .monospaced))
                            .foregroundColor(.white)
                    }
                    .padding(10)
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(8)
                    
                    // Cloud Status Banner
                    HStack {
                        Circle()
                            .fill(Color.green)
                            .frame(width: 7, height: 7)
                        Text("GOOGLE CLOUD FIRESTORE LINKED")
                            .font(.system(size: 10, weight: .heavy))
                            .foregroundColor(Color.green)
                        Spacer()
                        Text("LIVE SYNC ACTIVE")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Color.white.opacity(0.8))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.green.opacity(0.15))
                    .cornerRadius(6)
                }
                .padding(16)
                .background(ShiftTheme.headerBlack(for: scheme))
                .cornerRadius(12)
                
                // Section Title
                HStack {
                    Text("ACTIVE SHIFT ROSTER")
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                    Spacer()
                    Text("SELECT YOUR PROFILE")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                }
                .padding(.horizontal, 4)
                
                // Staff List
                LazyVStack(spacing: 8) {
                    ForEach(firestore.users) { user in
                        Button {
                            handleUserTap(user)
                        } label: {
                            HStack(spacing: 12) {
                                ZStack {
                                    Circle()
                                        .fill(ShiftTheme.headerBlack(for: scheme))
                                        .frame(width: 44, height: 44)
                                        .overlay(Circle().stroke(ShiftTheme.orange, lineWidth: 2))
                                    Text(user.initials)
                                        .font(.system(size: 15, weight: .black))
                                        .foregroundColor(.white)
                                }
                                
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(user.name)
                                        .font(.system(size: 14, weight: .black))
                                        .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                                    
                                    Text(user.role)
                                        .font(.system(size: 9, weight: .heavy))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(user.isAdmin ? Color.red.opacity(0.15) : Color.blue.opacity(0.15))
                                        .foregroundColor(user.isAdmin ? Color.red : Color.blue)
                                        .cornerRadius(4)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                            }
                            .padding(12)
                            .background(ShiftTheme.surface(for: scheme))
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
                        }
                    }
                }
            }
            .padding(12)
        }
        .background(ShiftTheme.bg(for: scheme))
        .onReceive(timer) { _ in
            updateClock()
        }
        .onAppear {
            updateClock()
        }
        .sheet(item: $selectedUserForPin) { user in
            pinPadSheet(for: user, scheme: scheme)
                .presentationDetents([.height(480)])
        }
        .sheet(isPresented: $showFirstTimePinSetup) {
            firstTimePinSheet(scheme: scheme)
                .presentationDetents([.height(460)])
        }
    }
    
    private func updateClock() {
        let now = Date()
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "HH:mm:ss"
        currentTimeStr = timeFormatter.string(from: now)
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "EEEE, d MMMM yyyy"
        currentDateStr = dateFormatter.string(from: now)
    }
    
    private func handleUserTap(_ user: RetailUser) {
        if user.pinHash.isEmpty {
            // First time sign-in: must choose PIN
            selectedUserForPin = user
            showFirstTimePinSetup = true
        } else {
            pinCode = ""
            pinError = nil
            selectedUserForPin = user
        }
    }
    
    // MARK: - PIN Pad Sheet
    private func pinPadSheet(for user: RetailUser, scheme: ColorScheme) -> some View {
        VStack(spacing: 16) {
            Capsule()
                .fill(Color.gray.opacity(0.5))
                .frame(width: 40, height: 4)
                .padding(.top, 10)
            
            Text(user.name)
                .font(.system(size: 16, weight: .black))
                .foregroundColor(ShiftTheme.textPrimary(for: scheme))
            
            Text("ENTER 4-DIGIT SECURITY PIN")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(ShiftTheme.textSecondary(for: scheme))
            
            // PIN Dots
            HStack(spacing: 16) {
                ForEach(0..<4) { index in
                    Circle()
                        .fill(index < pinCode.count ? ShiftTheme.orange : Color.clear)
                        .frame(width: 16, height: 16)
                        .overlay(Circle().stroke(ShiftTheme.orange, lineWidth: 2))
                }
            }
            .padding(.vertical, 8)
            
            if let error = pinError {
                Text(error)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.red)
            }
            
            // Keypad Grid
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                ForEach(1...9, id: \.self) { num in
                    keypadButton("\(num)", scheme: scheme) {
                        appendPin("\(num)", user: user)
                    }
                }
                keypadButton("CLEAR", scheme: scheme, isAction: true) {
                    pinCode = ""
                    pinError = nil
                }
                keypadButton("0", scheme: scheme) {
                    appendPin("0", user: user)
                }
                keypadButton("CANCEL", scheme: scheme, isAction: true) {
                    selectedUserForPin = nil
                }
            }
            .padding(.horizontal, 20)
            
            Spacer()
        }
        .padding()
        .background(ShiftTheme.surface(for: scheme))
    }
    
    private func keypadButton(_ label: String, scheme: ColorScheme, isAction: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: isAction ? 12 : 20, weight: .bold))
                .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(ShiftTheme.surfaceSubtle(for: scheme))
                .cornerRadius(10)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
        }
    }
    
    private func appendPin(_ digit: String, user: RetailUser) {
        guard pinCode.count < 4 else { return }
        pinCode.append(digit)
        if pinCode.count == 4 {
            if firestore.verifyUserPin(user, pin: pinCode) {
                firestore.currentUser = user
                firestore.touchActivity()
                selectedUserForPin = nil
                firestore.showToast("Welcome back, \(user.name)!")
            } else {
                pinError = "Incorrect PIN. Try again."
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    pinCode = ""
                }
            }
        }
    }
    
    // MARK: - First Time PIN Setup
    private func firstTimePinSheet(scheme: ColorScheme) -> some View {
        VStack(spacing: 14) {
            Capsule()
                .fill(Color.gray.opacity(0.5))
                .frame(width: 40, height: 4)
                .padding(.top, 10)
            
            Text("FIRST TIME SIGN-IN")
                .font(.system(size: 11, weight: .black))
                .foregroundColor(.purple)
            
            Text("Set Your Private 4-Digit PIN")
                .font(.system(size: 16, weight: .black))
                .foregroundColor(ShiftTheme.textPrimary(for: scheme))
            
            VStack(alignment: .leading, spacing: 4) {
                Text("NEW 4-DIGIT PIN")
                    .font(.system(size: 10, weight: .heavy))
                    .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                SecureField("••••", text: $newPin1)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text("CONFIRM 4-DIGIT PIN")
                    .font(.system(size: 10, weight: .heavy))
                    .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                SecureField("••••", text: $newPin2)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)
            }
            
            if let err = firstTimeError {
                Text(err)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.red)
            }
            
            Button {
                saveFirstTimePin()
            } label: {
                Text("SAVE PIN & START SHIFT")
                    .font(.system(size: 13, weight: .black))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Color.purple)
                    .cornerRadius(8)
            }
            
            Button("CANCEL") {
                showFirstTimePinSetup = false
                selectedUserForPin = nil
            }
            .font(.system(size: 12, weight: .bold))
            .foregroundColor(ShiftTheme.textSecondary(for: scheme))
            
            Spacer()
        }
        .padding(20)
        .background(ShiftTheme.surface(for: scheme))
    }
    
    private func saveFirstTimePin() {
        guard newPin1.count == 4, newPin1.allSatisfy({ $0.isNumber }) else {
            firstTimeError = "PIN must be exactly 4 numeric digits."
            return
        }
        guard newPin1 == newPin2 else {
            firstTimeError = "PINs do not match. Please re-enter."
            return
        }
        guard let user = selectedUserForPin else { return }
        
        Task {
            do {
                try await firestore.setUserPin(userId: user.id, pin: newPin1)
                firestore.currentUser = user
                firestore.touchActivity()
                showFirstTimePinSetup = false
                selectedUserForPin = nil
            } catch {
                firstTimeError = error.localizedDescription
            }
        }
    }
}
