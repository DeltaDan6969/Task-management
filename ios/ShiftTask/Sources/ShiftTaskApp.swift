import SwiftUI
import FirebaseCore

@main
public struct ShiftTaskApp: App {
    
    public init() {
        // Initialize Firebase with GoogleService-Info.plist
        if let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let options = FirebaseOptions(contentsOfFile: path) {
            FirebaseApp.configure(options: options)
        } else {
            FirebaseApp.configure()
        }
    }
    
    public var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
