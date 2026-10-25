import SwiftUI

/// Retail Material 3 Theme palette for ShiftTask iOS
public enum ShiftTheme {
    public static let orange = Color(red: 0.90, green: 0.32, blue: 0.00) // #E65100
    public static let orangeLight = Color(red: 1.00, green: 0.95, blue: 0.88) // #FFF3E0
    public static let greenDone = Color(red: 0.18, green: 0.49, blue: 0.20) // #2E7D32
    public static let greenLight = Color(red: 0.91, green: 0.96, blue: 0.91) // #E8F5E9
    public static let amber = Color(red: 0.96, green: 0.49, blue: 0.00) // #F57C00
    public static let amberLight = Color(red: 1.00, green: 0.97, blue: 0.88) // #FFF8E1
    public static let blue = Color(red: 0.10, green: 0.46, blue: 0.82) // #1976D2
    public static let blueLight = Color(red: 0.89, green: 0.95, blue: 0.99) // #E3F2FD
    
    // Light / Dark Adaptive Colors
    public static func bg(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.05, green: 0.06, blue: 0.08) : Color(red: 0.96, green: 0.96, blue: 0.97)
    }
    
    public static func surface(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.09, green: 0.10, blue: 0.13) : Color.white
    }
    
    public static func surfaceSubtle(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.12, green: 0.14, blue: 0.19) : Color(red: 0.98, green: 0.98, blue: 0.98)
    }
    
    public static func headerBlack(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.03, green: 0.04, blue: 0.05) : Color(red: 0.07, green: 0.07, blue: 0.07)
    }
    
    public static func border(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.16, green: 0.19, blue: 0.24) : Color(red: 0.90, green: 0.91, blue: 0.92)
    }
    
    public static func textPrimary(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.95, green: 0.96, blue: 0.96) : Color(red: 0.07, green: 0.10, blue: 0.15)
    }
    
    public static func textSecondary(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.61, green: 0.64, blue: 0.69) : Color(red: 0.42, green: 0.45, blue: 0.50)
    }
}
